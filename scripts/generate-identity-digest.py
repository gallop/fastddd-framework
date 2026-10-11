#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""FastDDD identity 契约摘要生成链（W7-b b-2，D-8 = (A) 生成物入仓 docs/）。

从 Maven 仓形态制品只读抽取每模块契约摘要 markdown（端口与方法签名 / 配置键 /
常量·词表·错误码 / javadoc 覆盖核对 / 迁移清单〔仅 reference-pg〕）。

- 单一事实源 = 制品（sources/javadoc/main jar），生成而非手写；真值仍是
  sources/javadoc jar 本体，本摘要仅导航面（冲突以制品为准）。
- 确定性输出：全排序、零时间戳、LF 换行——防漂移判据 = 同参数重生成 cmp 全等。
- 冻结代际只读：zipfile 只读打开，不向制品仓写入任何字节；唯一写入 = --out 目录。

已知局限（导航摘要的边界，b-3 抽样核对兜底）：
- 方法签名抽取为「剥注释 + 分号分段」启发式；default 方法体与嵌套类型声明不收录；
- record 类型不单列；嵌套类常量按类名就近归属，同文件同名嵌套类会合并；
- 配置键字段名经 relaxed-binding kebab 转换；嵌套 Properties 对象递归级联（限深 4）。

用法：
  python scripts/generate-identity-digest.py \
    --repo ../.fastddd-releases/internal-v1.1.0-m2/repository \
    --version 1.1.0-M2 --out docs/identity/digest
"""

import argparse
import hashlib
import io
import os
import re
import sys
import zipfile

GROUP_PATH = 'io/github/gallop/fastddd'
MODULES = [
    'fast-ddd-identity-core',
    'fast-ddd-identity-reference-pg',
    'fast-ddd-identity-spring-boot-starter',
    'fast-ddd-identity-feishu',
]

TYPE_RE = re.compile(r'(?<!@)\b(interface|class|enum)\s+(\w+)([^{;()=]*)\{')
CONST_RE = re.compile(
    r'^(?:(?:public|private|protected|static|final|transient|volatile)\s+)*'
    r'([\w.<>\[\], ]+?)\s+([\w$]+)\s*=\s*(.+?)\s*;?$')
FIELD_RE = re.compile(
    r'^(?:private|protected|public)\s+(?:static\s+)?(?:final\s+)?'
    r'([\w.<>\[\], ]+?)\s+([\w$]+)\s*(?:=\s*[^;]+)?$')
CP_RE = re.compile(r'@ConfigurationProperties\s*\(\s*(?:prefix\s*=\s*)?"([^"]+)"\s*\)')
VALUE_RE = re.compile(r'@Value\s*\(\s*"([^"]+)"')


def norm(text):
    return re.sub(r'\s+', ' ', text).strip()


def kebab(name):
    s1 = re.sub(r'(.)([A-Z][a-z]+)', r'\1-\2', name)
    return re.sub(r'([a-z0-9])([A-Z])', r'\1-\2', s1).lower()


# ---------------------------------------------------------------- 源码级工具


def _skip_string(s, i):
    """s[i] 为开引号；返回闭合引号之后的位置（处理 \\ 转义）。"""
    q = s[i]
    i += 1
    n = len(s)
    while i < n:
        if s[i] == '\\':
            i += 2
            continue
        if s[i] == q:
            return i + 1
        i += 1
    return n


def strip_comments(src):
    """剥除 // 与 /* */ 注释；字符串/字符字面量原样保留。"""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c in ('"', "'"):
            j = _skip_string(src, i)
            out.append(src[i:j])
            i = j
            continue
        if c == '/' and i + 1 < n and src[i + 1] == '/':
            while i < n and src[i] != '\n':
                i += 1
            continue
        if c == '/' and i + 1 < n and src[i + 1] == '*':
            i += 2
            while i + 1 < n and not (src[i] == '*' and src[i + 1] == '/'):
                i += 1
            i = min(i + 2, n)
            out.append(' ')
            continue
        out.append(c)
        i += 1
    return ''.join(out)


def match_brace(s, open_idx):
    """s[open_idx] == '{'，返回配对 '}' 的索引（字符串感知）。"""
    depth = 0
    i, n = open_idx, len(s)
    while i < n:
        c = s[i]
        if c in ('"', "'"):
            i = _skip_string(s, i)
            continue
        if c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return n - 1


def split_members(body):
    """类型体内成员切段（字符串感知）。depth 0 语句以 ';' 终止为 stmt 段；
    '{' 开启的嵌套体（default 方法 / 嵌套类型 / 注解数组初始化）为 block 段
    （仅保留头部文本，体跳过）。"""
    segs = []
    buf = []
    i, n = 0, len(body)
    while i < n:
        c = body[i]
        if c in ('"', "'"):
            j = _skip_string(body, i)
            buf.append(body[i:j])
            i = j
            continue
        if c == '{':
            segs.append(('block', norm(''.join(buf))))
            buf = []
            i += 1
            d = 1
            while i < n and d > 0:
                cc = body[i]
                if cc in ('"', "'"):
                    i = _skip_string(body, i)
                    continue
                if cc == '{':
                    d += 1
                elif cc == '}':
                    d -= 1
                i += 1
            continue
        if c == '}':
            i += 1
            continue
        if c == ';':
            segs.append(('stmt', norm(''.join(buf))))
            buf = []
            i += 1
            continue
        buf.append(c)
        i += 1
    return segs


def parse_enum_constants(body):
    """enum 体内首段（首个 ';' 或 '{' 之前）的常量标识符序列。"""
    i, n = 0, len(body)
    end = n
    while i < n:
        c = body[i]
        if c in ('"', "'"):
            i = _skip_string(body, i)
            continue
        if c == ';' or c == '{':
            end = i
            break
        i += 1
    head = body[:end]
    names = []
    for token in head.split(','):
        m = re.match(r'\s*([\w$]+)', token)
        if m and m.group(1) not in ('implements',):
            names.append(m.group(1))
    return names


# ---------------------------------------------------------------- 单文件解析


def parse_source(code):
    """剥注释后解析一个 .java：返回 (package, interfaces, enums, class_constants)。

    interfaces: [{name, header, methods: [...], constants: [(name, type, value)]}]
    enums:      [{name, constants: [...]}]
    class_constants: [(name, type, value)]（static final 字段，类级）
    """
    stripped = strip_comments(code.decode('utf-8'))
    pkg_m = re.search(r'package\s+([\w.]+)\s*;', stripped)
    pkg = pkg_m.group(1) if pkg_m else ''
    interfaces, enums, class_constants = [], [], []
    for m in TYPE_RE.finditer(stripped):
        kind, name, header = m.group(1), m.group(2), norm(m.group(3))
        close = match_brace(stripped, m.end() - 1)
        body = stripped[m.end():close]
        if kind == 'interface':
            iface = {'name': name, 'header': header, 'methods': [], 'constants': []}
            for seg_kind, seg in split_members(body):
                if not seg:
                    continue
                if seg_kind == 'block':
                    # default 方法：block 头部即完整签名
                    if seg.startswith('default '):
                        iface['methods'].append(seg[len('default '):])
                    continue
                if '=' in seg:
                    cm = CONST_RE.match(seg)
                    if cm:
                        iface['constants'].append(
                            (cm.group(2), norm(cm.group(1)), cm.group(3)))
                elif '(' in seg and not re.match(
                        r'(?:public\s+|private\s+|protected\s+|static\s+|abstract\s+|final\s+)*'
                        r'(?:class|interface|enum|record)\b', seg):
                    iface['methods'].append(seg)
            interfaces.append(iface)
        elif kind == 'enum':
            enums.append({'name': name, 'constants': parse_enum_constants(body)})
        else:  # class（嵌套类常量按类名就近归属：holder = 最近外层类名）
            holder = (pkg + '.' if pkg else '') + name
            for seg_kind, seg in split_members(body):
                if seg_kind != 'stmt' or '=' not in seg:
                    continue
                if not re.search(r'\bstatic\b', seg) or not re.search(r'\bfinal\b', seg):
                    continue
                cm = CONST_RE.match(seg)
                if cm:
                    class_constants.append(
                        (holder, cm.group(2), norm(cm.group(1)), cm.group(3)))
    return pkg, interfaces, enums, class_constants


def collect_props(prefix, class_name, stripped, fields, depth=0):
    """从 @ConfigurationProperties 类（含嵌套 Properties 级联）收集配置键。"""
    if depth > 4:
        return
    m = re.search(r'\bclass\s+' + re.escape(class_name) + r'\b[^{;()=]*\{', stripped)
    if not m:
        return
    close = match_brace(stripped, m.end() - 1)
    body = stripped[m.end():close]
    for seg_kind, seg in split_members(body):
        if seg_kind != 'stmt' or not seg:
            continue
        fm = FIELD_RE.match(seg)
        if not fm:
            continue
        ftype, fname = norm(fm.group(1)), fm.group(2)
        nm = re.search(r'(\w+)Properties$', ftype)
        if nm:
            collect_props(prefix + '.' + kebab(fname), nm.group(1),
                          stripped, fields, depth + 1)
        else:
            fields.append((prefix + '.' + kebab(fname), ftype))


# ---------------------------------------------------------------- 模块级抽取


def sha256_bytes(data):
    return hashlib.sha256(data).hexdigest()


def read_jar(repo, module, version, suffix):
    path = os.path.join(repo, GROUP_PATH, module, version,
                        '%s-%s%s.jar' % (module, version, suffix))
    if not os.path.isfile(path):
        sys.stderr.write('FATAL: 缺失制品 %s\n' % path)
        sys.exit(1)
    with open(path, 'rb') as f:
        data = f.read()
    return path, data


def build_digest(repo, module, version, cmd_line):
    _, main_data = read_jar(repo, module, version, '')
    _, src_data = read_jar(repo, module, version, '-sources')
    _, jd_data = read_jar(repo, module, version, '-javadoc')
    shas = {'main': sha256_bytes(main_data), 'sources': sha256_bytes(src_data),
            'javadoc': sha256_bytes(jd_data)}

    interfaces, enums, constants, config_keys, value_keys = [], [], [], [], []
    with zipfile.ZipFile(io_bytes(src_data)) as zsrc:
        for name in sorted(zsrc.namelist()):
            if not name.endswith('.java'):
                continue
            code = zsrc.read(name)
            stripped = strip_comments(code.decode('utf-8'))
            pkg, ifaces, enms, cls_consts = parse_source(code)
            for it in ifaces:
                it['fqn'] = (pkg + '.' if pkg else '') + it['name']
            interfaces.extend(ifaces)
            for e in enms:
                e['fqn'] = (pkg + '.' if pkg else '') + e['name']
            enums.extend(enms)
            constants.extend(cls_consts)
            for cp in CP_RE.finditer(stripped):
                cm = re.search(r'\bclass\s+(\w+)', stripped[cp.end():])
                if cm:
                    collect_props(cp.group(1), cm.group(1), stripped, config_keys)
            value_keys.extend(VALUE_RE.findall(stripped))

    interfaces.sort(key=lambda d: d['fqn'])
    enums.sort(key=lambda d: d['fqn'])
    constants.sort(key=lambda c: c[0])
    config_keys = sorted(set(config_keys))
    value_keys = sorted(set(value_keys))

    # javadoc 覆盖核对
    with zipfile.ZipFile(io_bytes(jd_data)) as zjd:
        jd_names = set(zjd.namelist())
    jd_ok, jd_miss = [], []
    for it in interfaces:
        rel = it['fqn'].replace('.', '/') + '.html'
        (jd_ok if rel in jd_names else jd_miss).append(it['fqn'])

    # 迁移清单（仅 reference-pg main jar 携带 db/identity/）
    migrations = []
    with zipfile.ZipFile(io_bytes(main_data)) as zmain:
        for name in sorted(zmain.namelist()):
            if not (name.startswith('db/identity/') and name.endswith('.sql')):
                continue
            data = zmain.read(name)
            text = data.decode('utf-8')
            ddl = [norm(seg) for seg in
                   re.findall(r'CREATE\s+(?:UNIQUE\s+)?(?:TABLE|INDEX|VIEW)[^;]*', text, re.I)]
            migrations.append({'path': name, 'sha256': sha256_bytes(data),
                               'lines': text.count('\n') + 1, 'ddl': ddl})

    return render(module, version, cmd_line, shas, interfaces, enums, constants,
                  config_keys, value_keys, jd_ok, jd_miss, migrations)


def io_bytes(data):
    return io.BytesIO(data)


def render(module, version, cmd_line, shas, interfaces, enums, constants,
           config_keys, value_keys, jd_ok, jd_miss, migrations):
    L = []
    w = L.append
    w('# 契约摘要：%s（%s）' % (module, version))
    w('')
    w('> **生成物（生成而非手写）**：由 `scripts/generate-identity-digest.py` 从 Maven 仓形态制品抽取；'
      '单一事实源 = 制品——**冲突以 sources/javadoc jar 为准，本摘要仅导航面**。')
    w('> 生成命令：`%s`' % cmd_line)
    w('')
    w('| 源制品 | SHA-256 |')
    w('|---|---|')
    for suffix, label in (('', 'main'), ('-sources', 'sources'), ('-javadoc', 'javadoc')):
        w('| `%s-%s%s.jar` | `%s` |' % (module, version, suffix, shas[label]))
    w('')
    w('## 1. 端口契约（接口与方法签名，%d 个）' % len(interfaces))
    w('')
    if interfaces:
        for it in interfaces:
            w('### %s' % it['fqn'])
            if it['header']:
                w('')
                w('`%s`' % it['header'])
            w('')
            for meth in it['methods']:
                w('- `%s`' % meth)
            for cname, ctype, cvalue in it['constants']:
                w('- 常量 `%s %s = %s`' % (ctype, cname, cvalue))
            w('')
    else:
        w('（sources 无 interface 声明）')
        w('')
    w('## 2. 配置键（@ConfigurationProperties 绑定，%d 个）' % len(config_keys))
    w('')
    if config_keys:
        w('| 键 | 字段类型 |')
        w('|---|---|')
        for key, ftype in config_keys:
            w('| `%s` | `%s` |' % (key, ftype))
    else:
        w('（无 @ConfigurationProperties 绑定）')
    w('')
    w('### @Value 注入键（%d 个）' % len(value_keys))
    w('')
    if value_keys:
        for key in value_keys:
            w('- `%s`' % key)
    else:
        w('（无 @Value 注入）')
    w('')
    w('## 3. 常量、词表与错误码（static final %d + enum %d）' % (len(constants), len(enums)))
    w('')
    for holder, cname, ctype, cvalue in constants:
        w('- `%s.%s` = `%s`（%s）' % (holder, cname, cvalue, ctype))
    for e in enums:
        w('- enum `%s`：`%s`' % (e['fqn'], '` / `'.join(e['constants'])))
    if not constants and not enums:
        w('（无 static final 常量 / enum 词表）')
    w('')
    w('## 4. javadoc 覆盖核对（%d/%d）' % (len(jd_ok), len(jd_ok) + len(jd_miss)))
    w('')
    for fqn in jd_ok:
        w('- ✓ `%s`' % fqn)
    for fqn in jd_miss:
        w('- ✗ `%s`（-javadoc.jar 缺对应 html）' % fqn)
    w('')
    w('## 5. 迁移清单（db/identity/，%d 件）' % len(migrations))
    w('')
    if migrations:
        w('| 文件 | SHA-256 | 行数 |')
        w('|---|---|---|')
        for mig in migrations:
            w('| `%s` | `%s` | %d |' % (mig['path'], mig['sha256'], mig['lines']))
        w('')
        for mig in migrations:
            w('`%s` DDL：' % mig['path'])
            w('')
            for ddl in mig['ddl']:
                w('- `%s`' % ddl)
            w('')
    else:
        w('（本模块 main jar 无 db/identity/ 迁移——迁移仅随 reference-pg 制品交付）')
        w('')
    return '\n'.join(L) + '\n'


def main():
    ap = argparse.ArgumentParser(description='FastDDD identity 契约摘要生成链（W7-b b-2）')
    ap.add_argument('--repo', required=True, help='Maven 代际 repository 路径（只读）')
    ap.add_argument('--version', required=True, help='制品版本（如 1.1.0-M2）')
    ap.add_argument('--out', default='docs/identity/digest', help='摘要输出目录')
    args = ap.parse_args()
    cmd_line = ('python scripts/generate-identity-digest.py --repo %s --version %s --out %s'
                % (args.repo, args.version, args.out))
    os.makedirs(args.out, exist_ok=True)
    total_lines = 0
    for idx, module in enumerate(MODULES, 1):
        text = build_digest(args.repo, module, args.version, cmd_line)
        out_path = os.path.join(args.out, module + '.md')
        with open(out_path, 'w', encoding='utf-8', newline='\n') as f:
            f.write(text)
        lines = text.count('\n')
        total_lines += lines
        print('[%d/%d] %s → %s（%d 行）' % (idx, len(MODULES), module, out_path, lines))
    print('PASS：4 模块 digest 生成完毕（合计 %d 行）；判据下一步 = 同参数重生成 cmp 全等'
          % total_lines)
    return 0


if __name__ == '__main__':
    sys.exit(main())

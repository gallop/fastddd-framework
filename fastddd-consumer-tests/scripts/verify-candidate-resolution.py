#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""W4-b b3：候选 run 解析来源断言（forked build.log 校验脚本，行为级判据）。

判据来源 = [W4 M1 制品交付执行拆分](../../docs/identity/W4%20M1%20制品交付执行拆分.md) v2 §4 b3，
其中已并入 W4 拆分 v2 复评两项口径：
  - N-1：forked 命令行断言的载体是**外层 -X 调试日志**（`-Dinvoker.debug=true` 打开的是
    forked 的 -X，不含外层 Executing 行）；
  - N-2：正向版本断言在 INFO 级 forked 日志无载体（本地仓命中静默），主载体改为
    **文件系统级** = 隔离仓 io/github/gallop/fastddd/*/ 目录树的版本段。

判据清单：
  ④ 前置健全性：--its-dir 下 */build.log 数量 = --expected-scenarios，非空且含 BUILD SUCCESS
    （缺日志不得静默通过）；
  ① 零 fastddd 远程解析：所有 build.log 无任何指向 io/github/gallop/fastddd 的
    Downloading/Downloaded 行（候选 fastddd 制品只允许从隔离仓本地命中；该判据涵盖且强于
    "零 fastddd -SNAPSHOT 解析"）；
  ② 正向版本（文件系统级）：隔离仓 fastddd 坐标数 = --expected-coordinates，且每个坐标的
    版本目录集合恰为 {--expected-version}；
  ③'(a) 在线 run 交叉核对：全部 "Downloaded from" 成功行的 URL 路径映射到隔离仓内实际存在的
    文件（在线首跑的第三方预热落盘证明 forked 本地仓 = 隔离仓）；
  ③'(b) 机制证据（--outer-debug-log）：外层 -X 日志中 forked 命令行含
    -Dmaven.repo.local=<隔离仓> 与 -Dfastddd.version=<expected-version>；
  --offline（b7 离线 run）：所有 build.log 全量零 Downloading/Downloaded 行。

用法示例：
  python verify-candidate-resolution.py \
    --its-dir fastddd-consumer-tests/target/its \
    --repo D:/usr/workspace_2026/.fastddd-releases/candidate-v1.1.0-m1/repository \
    --expected-scenarios 31 --expected-coordinates 21 --expected-version 1.1.0-M1 \
    [--outer-debug-log <外层 -X 日志>] [--offline]

退出码：0 = 全部判据通过；1 = 存在违例（清单打印于 stdout）。
解析簿记残留（*.lastUpdated / _remote.repositories）仅登记备查，不计违例。
"""
import argparse
import glob
import os
import re
import sys

# forked build.log 中传输行带 Maven 级别前缀（"[INFO] Downloading from ..."），前缀置为可选
DL_LINE = re.compile(r'^(?:\[(?:INFO|WARNING|ERROR)\] )?(Downloading|Downloaded) from\s+(\S+):\s+(\S+)')
FASTDDD_PATH = 'io/github/gallop/fastddd'


def main():
    ap = argparse.ArgumentParser(description='W4-b b3 候选 run 解析来源断言')
    ap.add_argument('--its-dir', required=True, help='invoker cloneProjectsTo 目录（target/its）')
    ap.add_argument('--repo', required=True, help='隔离候选仓路径')
    ap.add_argument('--expected-scenarios', type=int, required=True, help='预期场景数（按执行机 Building 清单计数）')
    ap.add_argument('--expected-coordinates', type=int, default=21, help='隔离仓预期 fastddd 坐标数（默认 21）')
    ap.add_argument('--expected-version', required=True, help='候选版本（如 1.1.0-M1）')
    ap.add_argument('--offline', action='store_true', help='b7 离线模式：断言全量零远程下载行')
    ap.add_argument('--outer-debug-log', help='外层 -X 调试日志（③(b) 机制证据，N-1 通道）')
    args = ap.parse_args()

    violations = []
    repo = args.repo.replace('\\', '/').rstrip('/')

    # ④ 前置健全性
    logs = sorted(glob.glob(os.path.join(args.its_dir, '*', 'build.log')))
    names = [os.path.basename(os.path.dirname(l)) for l in logs]
    print(f'[iv] build.log 数量 = {len(logs)}（预期 {args.expected_scenarios}）')
    print(f'[iv] 场景清单 = {", ".join(names)}')
    if len(logs) != args.expected_scenarios:
        violations.append(f'④ build.log 数量 {len(logs)} != 预期 {args.expected_scenarios}')

    downloaded = {}  # url -> repo id（仅 "Downloaded" 成功行）
    dl_attempt = 0
    for log, scenario in zip(logs, names):
        try:
            with open(log, encoding='utf-8', errors='replace') as fh:
                text = fh.read()
        except OSError as exc:
            violations.append(f'④ 无法读取 {log}: {exc}')
            continue
        if not text.strip():
            violations.append(f'④ {scenario}/build.log 为空')
        if 'BUILD SUCCESS' not in text:
            violations.append(f'④ {scenario}/build.log 无 BUILD SUCCESS')
        for line in text.splitlines():
            m = DL_LINE.match(line)
            if not m:
                continue
            kind, rid, url = m.groups()
            dl_attempt += 1
            if args.offline:
                violations.append(f'[offline] {scenario}: 离线 run 出现远程下载行：{line.strip()}')
            if FASTDDD_PATH in url:
                violations.append(f'[i] {scenario}: fastddd 远程解析行：{line.strip()}')
            if kind == 'Downloaded':
                downloaded[url] = rid
    print(f'[i] 扫描下载行总数 = {dl_attempt}（fastddd 违例行见违例清单）')

    # ② 正向版本（N-2：文件系统级主载体；统一正斜杠拼接，规避 Windows 盘符相对路径陷阱）
    base = repo + '/io/github/gallop/fastddd'
    coords = {}
    if os.path.isdir(base):
        for coord in sorted(os.listdir(base)):
            cdir = os.path.join(base, coord)
            if not os.path.isdir(cdir):
                continue
            coords[coord] = sorted(d for d in os.listdir(cdir) if os.path.isdir(os.path.join(cdir, d)))
    print(f'[ii] 隔离仓 fastddd 坐标数 = {len(coords)}（预期 {args.expected_coordinates}）')
    if len(coords) != args.expected_coordinates:
        violations.append(f'② 坐标数 {len(coords)} != 预期 {args.expected_coordinates}')
    for coord, versions in coords.items():
        bad = [x for x in versions if x != args.expected_version]
        if bad:
            violations.append(f'② {coord} 存在非候选版本目录：{bad}')
    all_versions = sorted({x for versions in coords.values() for x in versions})
    print(f'[ii] 版本目录全集 = {all_versions}')
    if all_versions != [args.expected_version]:
        violations.append(f'② 版本目录全集 {all_versions} != [{args.expected_version}]')
    stray = []
    for root, _dirs, files in os.walk(base):
        for f in files:
            if f.endswith('.lastUpdated') or f == '_remote.repositories':
                stray.append(os.path.relpath(os.path.join(root, f), args.repo))
    if stray:
        print(f'[ii] 解析簿记残留（登记备查，不计违例）：{len(stray)} 个，样本：{stray[:10]}')

    # ③'(a) 在线 run 交叉核对（--offline 时跳过）。镜像 URL 常带路径前缀（aliyun 组仓
    # /nexus/content/groups/public/），不能按"去主机名即仓内路径"直映射；改用斜杠对齐
    # 后缀存在性核对：URL 路径在各 '/' 边界的后缀候选命中隔离仓任一文件相对路径即算落盘。
    if args.offline:
        print("[iii-a] 离线模式：交叉核对跳过（零下载行断言已在逐日志检查）")
    elif downloaded:
        from urllib.parse import urlsplit
        relpaths = set()
        for root, _dirs, files in os.walk(args.repo):
            for f in files:
                relpaths.add(os.path.relpath(os.path.join(root, f), args.repo).replace('\\', '/'))
        ok = miss = 0
        missed = []
        for url in downloaded:
            upath = urlsplit(url).path.lstrip('/')
            segs = upath.split('/')
            hit = any('/'.join(segs[i:]) in relpaths for i in range(len(segs)))
            if hit:
                ok += 1
            else:
                miss += 1
                missed.append(upath)
        print(f'[iii-a] Downloaded 成功行 {len(downloaded)} 条：交叉核对 存在={ok} 缺失={miss}')
        for p in missed[:10]:
            print(f'[iii-a] 缺失样本：{p}')
        if miss:
            violations.append(f"③'(a) 隔离仓缺失已下载文件 {miss} 个")
    else:
        print('[iii-a] 无 Downloaded 行——在线首跑（b6）应含第三方预热下载，请确认 run 形态')

    # ③'(b) 机制证据（N-1：外层 -X 通道）。Windows invoker 命令行引用形态为
    # "cmd.exe /X /C \"mvn.cmd -B -D maven.repo.local=... -D fastddd.version=...\""，
    # -D 后可带空格，两条探测均用正则容忍（实测见 W4-b 执行记录 b5）。
    if args.outer_debug_log:
        with open(args.outer_debug_log, encoding='utf-8', errors='replace') as fh:
            xtext = fh.read()
        repo_re = re.compile(r'-D\s*maven\.repo\.local=(\S+)')
        ver_re = re.compile(r'-D\s*fastddd\.version=' + re.escape(args.expected_version) + r'(?![\w.\-])')
        hits_repo = [ln.strip() for ln in xtext.splitlines() if repo_re.search(ln)]
        hits_ver = [ln.strip() for ln in xtext.splitlines() if ver_re.search(ln)]
        ver_prop = f'-Dfastddd.version={args.expected_version}（含带空格形态）'
        print(f'[iii-b] 外层调试日志含 -D maven.repo.local= 的 forked 命令行行数 = {len(hits_repo)}')
        print(f'[iii-b] 含 {ver_prop} 的行数 = {len(hits_ver)}')
        if not hits_repo:
            violations.append("③'(b) 外层调试日志未见 forked -Dmaven.repo.local=<隔离仓>")
        elif not (any(repo.replace('/', os.sep) in h for h in hits_repo) or any(repo in h for h in hits_repo)):
            violations.append("③'(b) -Dmaven.repo.local 未指向隔离仓")
        if not hits_ver:
            violations.append(f"③'(b) 外层调试日志未见 {ver_prop}")
        if hits_repo:
            print(f'[iii-b] 命令行样本：{hits_repo[0][:300]}')

    print()
    if violations:
        print(f'判定：FAIL（违例 {len(violations)} 项）')
        for s in violations:
            print('  -', s)
        sys.exit(1)
    passed = ['④ 前置健全性', '① 零 fastddd 远程解析', '② 文件系统版本轴']
    if not args.offline:
        passed.append("③'(a) 在线交叉核对")
    if args.outer_debug_log:
        passed.append('③(b) 机制证据')
    if args.offline:
        passed.append('offline 零下载行')
    print('判定：PASS（' + ' + '.join(passed) + '）')
    sys.exit(0)


if __name__ == '__main__':
    main()

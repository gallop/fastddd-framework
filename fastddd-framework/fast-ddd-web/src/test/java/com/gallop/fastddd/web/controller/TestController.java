package com.gallop.fastddd.web.controller;

import com.gallop.fastddd.application.exception.CommandValidationException;
import com.gallop.fastddd.common.exception.BusinessException;
import com.gallop.fastddd.common.exception.SystemException;
import com.gallop.fastddd.common.result.Result;
import com.gallop.fastddd.web.response.ApiResponse;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 测试控制器
 * 用于验证Web配置和全局异常处理是否正常工作
 * 
 * @author FastDDD Framework Team
 * @version 1.0.0-SNAPSHOT
 */
@RestController
@RequestMapping("/test")
public class TestController {
    
    /**
     * 测试成功响应
     */
    @GetMapping("/success")
    public Result<String> testSuccess() {
        return Result.success("Hello FastDDD!");
    }
    
    /**
     * 测试字符串返回值（用于ResponseWrapper测试）
     */
    @GetMapping("/string")
    public String testString() {
        return "Hello String!";
    }
    
    /**
     * 测试业务异常
     */
    @GetMapping("/business-error")
    public Result<String> testBusinessError() {
        throw new BusinessException(400, "这是一个业务异常测试");
    }

    /**
     * 测试语义化错误码业务异常（(String,String) 构造器，errorCode=null）
     * <p>复现 GlobalExceptionHandler NPE 场景：errorCode 为 null 不应崩溃，且应透传 errCode。
     */
    @GetMapping("/business-error-coded")
    public Result<String> testBusinessErrorCoded() {
        throw new BusinessException("BUSINESS_CODED_ERROR", "语义化错误码业务异常");
    }
    
    /**
     * 测试系统异常
     */
    @GetMapping("/system-error")
    public Result<String> testSystemError() {
        throw new RuntimeException("这是一个系统异常测试");
    }
    
    /**
     * 测试参数校验
     */
    @PostMapping("/validation")
    public Result<String> testValidation(@Valid @RequestBody TestRequest request) {
        return Result.success("参数校验通过: " + request.getName());
    }
    
    /**
     * 测试Long类型序列化
     */
    @GetMapping("/long-serialization")
    public Result<TestResponse> testLongSerialization() {
        TestResponse response = new TestResponse();
        response.setId(9223372036854775807L); // Long.MAX_VALUE
        response.setName("Long序列化测试");
        return Result.success(response);
    }
    
    /**
     * 测试字符串返回值自动包装
     */
    @GetMapping("/wrapper/string")
    public String testStringWrapper() {
        return "Hello ApiResponse Wrapper";
    }
    
    /**
     * 测试对象返回值自动包装
     */
    @GetMapping("/wrapper/object")
    public TestResponse testObjectWrapper() {
        TestResponse response = new TestResponse();
        response.setId(123L);
        response.setName("自动包装测试");
        return response;
    }
    
    /**
     * 测试ApiResponse返回值（不应该被重复包装）
     */
    @GetMapping("/wrapper/api-response")
    public ApiResponse<String> testApiResponseWrapper() {
        return ApiResponse.success("已包装的ApiResponse");
    }
    
    /**
     * 测试null返回值自动包装
     */
    @GetMapping("/wrapper/null")
    public Object testNullWrapper() {
        return null;
    }
    
    /**
     * 测试参数类型错误
     */
    @GetMapping("/param-error")
    public Result<String> testParamError(@RequestParam("id") Integer id) {
        return Result.success("ID: " + id);
    }

    /**
     * 测试 Command 验证异常
     */
    @GetMapping("/command-validation-error")
    public Result<String> testCommandValidationError() {
        CommandValidationException validationException = new CommandValidationException(List.of(
                new CommandValidationException.ValidationError("orderNo", "订单号不能为空"),
                new CommandValidationException.ValidationError("amount", "金额必须大于0")
        ));
        throw validationException;
    }
    
    /**
     * 测试请求对象
     */
    public static class TestRequest {
        @NotBlank(message = "名称不能为空")
        private String name;
        
        @NotNull(message = "年龄不能为空")
        private Integer age;
        
        // getters and setters
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public Integer getAge() {
            return age;
        }
        
        public void setAge(Integer age) {
            this.age = age;
        }
    }
    
    /**
     * 测试响应对象
     */
    public static class TestResponse {
        private Long id;
        private String name;
        
        // getters and setters
        public Long getId() {
            return id;
        }
        
        public void setId(Long id) {
            this.id = id;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
    }
}
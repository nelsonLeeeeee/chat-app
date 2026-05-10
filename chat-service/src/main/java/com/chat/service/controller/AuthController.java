package com.chat.service.controller;

import com.chat.common.entity.SysUser;
import com.chat.common.result.Result;
import com.chat.service.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource
    private UserService userService;

    @PostMapping("/login")
    public Result<SysUser> login(@RequestParam String username,
                                 @RequestParam String password) {
        SysUser user = userService.login(username, password);
        if (user == null) {
            return Result.fail("用户名或密码错误");
        }
        return Result.ok(user);
    }

    @PostMapping("/register")
    public Result<SysUser> register(@RequestBody SysUser user) {
        SysUser registered = userService.register(user);
        return Result.ok(registered);
    }
}

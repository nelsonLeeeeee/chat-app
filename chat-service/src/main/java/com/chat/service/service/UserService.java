package com.chat.service.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chat.common.entity.SysUser;

public interface UserService extends IService<SysUser> {

    /**
     * 用户登录
     */
    SysUser login(String username, String password);

    /**
     * 用户注册
     */
    SysUser register(SysUser user);
}

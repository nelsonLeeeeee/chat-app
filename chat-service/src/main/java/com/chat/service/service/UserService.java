package com.chat.service.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chat.common.entity.SysUser;

/**
 * 用户服务接口，定义用户认证与注册契约
 */
public interface UserService extends IService<SysUser> {

    /**
     * 校验用户名密码，返回登录用户
     */
    SysUser login(String username, String password);

    /**
     * 注册新用户，密码加密后保存
     */
    SysUser register(SysUser user);
}

package com.chat.service.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chat.common.entity.SysUser;
import com.chat.service.mapper.UserMapper;
import com.chat.service.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, SysUser> implements UserService {

    @Override
    public SysUser login(String username, String password) {
        String encrypted = DigestUtil.md5Hex(password);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username)
               .eq(SysUser::getPassword, encrypted)
               .eq(SysUser::getStatus, 1);
        return getOne(wrapper);
    }

    @Override
    public SysUser register(SysUser user) {
        user.setPassword(DigestUtil.md5Hex(user.getPassword()));
        user.setRole("USER");
        user.setStatus(1);
        save(user);
        return user;
    }
}

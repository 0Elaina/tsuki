package com.nep.tsuki.user.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nep.tsuki.user.model.entity.UserAccount;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {

}

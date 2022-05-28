package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private ObjectMapper objectMapper;

    /**
     * 校验手机号
     * 如果不符合，返回错误信息
     * 符合，生成验证码
     * 保存验证码到session
     * 发送验证码
     *
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        final String code = RandomUtil.randomNumbers(6);
        session.setAttribute("code", code);
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, Duration.ofMinutes(RedisConstants.LOGIN_CODE_TTL));
        log.debug("短信验证码{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        final String code = loginForm.getCode();
        final String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        final String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if (code == null || !code.equals(cacheCode)) {
            return Result.fail("验证码错误");
        }
        //根据手机号查询用户
        User user = baseMapper.selectOne(new QueryWrapper<User>().eq("phone", phone));
        if (user == null) {
            //创建
            user = createUserWithPhone(phone);
        }
        final UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
//        session.setAttribute("user",userDTO);
        final String token = UUID.randomUUID().toString(true);
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())));
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, Duration.ofSeconds(RedisConstants.LOGIN_USER_TTL));
        return Result.ok(token);
    }

    @Override
    public Result sign(Long userId, LocalDateTime now) {
        String key = RedisConstants.USER_SIGN_KEY + userId + now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        final int dayOfMonth = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);

        return Result.ok();
    }

    @Override
    public Result signCount(Long userId) {
        final LocalDateTime now = LocalDateTime.now();
        String key = RedisConstants.USER_SIGN_KEY + userId + now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        final int dayOfMonth = now.getDayOfMonth();
        //bitfield sign:1011:202205 get u28 0
        final List<Long> values = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(BitFieldSubCommands.Offset.offset(0L)));
        if (CollectionUtil.isEmpty(values)) {
            return Result.ok(0);
        }
        Long num = values.get(0);
        int count = 0;
        while (num > 0) {
            if ((num & 1) == 0) {
                break;
            }
            num >>>= 1;
            count++;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_" + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}

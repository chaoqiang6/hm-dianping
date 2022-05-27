package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {


    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;


    @Override
    public Result isFollow(Long userId) {
        final Long followUserId = UserHolder.getUser().getId();
        final Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        if (count > 0) {
            return Result.ok(true);
        }
        return Result.ok(false);
    }

    @Override
    public Result followOrNot(Long userId, Boolean isFollow) {
        final Long curUserId = UserHolder.getUser().getId();
        if (isFollow) {
            final Integer exist = query().eq("user_id", userId).eq("follow_user_id", curUserId).count();
            if (exist > 0) {
                return Result.ok();
            }
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(curUserId);
            save(follow);
            stringRedisTemplate.opsForSet().add(RedisConstants.USER_FOLLOW_KEY+curUserId,userId.toString());

        } else {
            remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", curUserId));
            stringRedisTemplate.opsForSet().remove(RedisConstants.USER_FOLLOW_KEY+curUserId,userId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result getCommonUsers(Long userId) {
        final Long curUserId = UserHolder.getUser().getId();
        final Set<String> commonUserIds = stringRedisTemplate.opsForSet().intersect(RedisConstants.USER_FOLLOW_KEY + curUserId, RedisConstants.USER_FOLLOW_KEY + userId);
        if(CollectionUtil.isEmpty(commonUserIds)){
            return Result.ok(Collections.emptyList());
        }
        final List<User> commonFollowUsers = commonUserIds.stream().map(id -> userService.getById(id)).collect(Collectors.toList());

        return Result.ok(commonFollowUsers);
    }
}

package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    @Resource
    private IFollowService followService;

    @Override
    public Result queryBlogById(Long id) {
        final Blog records = getById(id);
        setUserInfo(records);
        isBlogLiked(records);
        return Result.ok(records);
    }

    private void isBlogLiked(Blog records) {
        final UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        final Long userId = user.getId();
        final Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + records.getId(), userId.toString());
        records.setIsLike(score != null);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            setUserInfo(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 点赞功能，如果当前用户未点赞，点赞，如果当前用户已点赞，取消点赞
     *
     * @param id
     */
    @Override
    public void likeBlog(Long id) {
        final Long userId = UserHolder.getUser().getId();
        final Double isLiked = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + id, userId.toString());
        if (isLiked != null) {
            final boolean updateSuc = update().setSql("liked = liked-1").eq("id", id).update();
            if (updateSuc) {
                stringRedisTemplate.opsForZSet().remove(RedisConstants.BLOG_LIKED_KEY + id, userId.toString());
            }
        } else {
            final boolean updateSuc = update().setSql("liked = liked+1").eq("id", id).update();
            if (updateSuc) {
                stringRedisTemplate.opsForZSet().add(RedisConstants.BLOG_LIKED_KEY + id, userId.toString(), System.currentTimeMillis());
            }
        }


    }

    @Override
    public Result queryBlogLikes(Long id) {
        //获取blog点赞的前五名
        final Set<String> firstLikedUsers = stringRedisTemplate.opsForZSet().range(RedisConstants.BLOG_LIKED_KEY + id, 0L, 4L);
        final List<Long> userIdList = firstLikedUsers.stream().map(Long::valueOf).collect(Collectors.toList());
        final String idJoin = StrUtil.join(",", userIdList);
        if (CollectionUtil.isEmpty(firstLikedUsers)) {
            return Result.ok(new ArrayList<>());
        }
        final List<UserDTO> firstLikedUserDtos = userService.query().in("id", userIdList).last("ORDER BY FIELD(id," + idJoin + ")").list().stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(firstLikedUserDtos);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        save(blog);
        //向关注的粉丝推送动态信息
        final List<Follow> follows = followService.query().eq("user_id", user.getId()).list();
        for (Follow follow : follows) {
            //向关注用户推送消息
            stringRedisTemplate.opsForZSet().add(RedisConstants.FEED_KEY + follow.getFollowUserId(), blog.getId().toString(), System.currentTimeMillis());
        }
        return Result.ok();
    }

    /**
     * @param lastId 上次查看消息id
     * @param offset 偏移量大小
     *
     * ZREVRANGEBYLEX key max min [LIMIT offset count]
     *   summary: Return a range of members in a sorted set, by lexicographical range, ordered from higher to lower strings.
     *   since: 2.8.9
     */
    @Override
    public Result queryBlogOfFollow(Long lastId, Integer offset) {
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(Collections.EMPTY_LIST);
        final Long userId = UserHolder.getUser().getId();
        final Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(RedisConstants.FEED_KEY + userId, 0, lastId, offset, 3);
        if (CollectionUtil.isEmpty(typedTuples)) {
            return Result.ok(scrollResult);
        }
        Long minTime = typedTuples.stream().map(ZSetOperations.TypedTuple::getScore).min(Comparator.naturalOrder()).get().longValue();
        offset = Long.valueOf(typedTuples.stream().filter(stringTypedTuple -> minTime.equals(stringTypedTuple.getScore().longValue())).count()).intValue();
        final List<String> blogIds = typedTuples.stream().map(ZSetOperations.TypedTuple::getValue).collect(Collectors.toList());
        final List<Blog> blogs = query().in("id", blogIds).last("ORDER BY FIELD(id," + StrUtil.join(",", blogIds) + ")").list();
        scrollResult.setList(blogs);
        scrollResult.setOffset(offset);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }

    private void setUserInfo(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }


}

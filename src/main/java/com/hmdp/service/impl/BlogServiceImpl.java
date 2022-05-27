package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
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
    @Override
    public Result queryBlogById(Long id) {
        final Blog records = getById(id);
        setUserInfo(records);

        return Result.ok(records);
    }

    private void isBlogLiked(Blog records) {
        final Long userId = UserHolder.getUser().getId();
        final Boolean isLiked = stringRedisTemplate.opsForSet().isMember(RedisConstants.BLOG_LIKED_KEY + records.getId(), userId.toString());
        records.setIsLike(BooleanUtil.isTrue(isLiked));
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
        records.forEach(this::setUserInfo);
        return Result.ok(records);
    }

    /**
     * 点赞功能，如果当前用户未点赞，点赞，如果当前用户已点赞，取消点赞
     * @param id
     */
    @Override
    public void likeBlog(Long id) {
        final Long userId = UserHolder.getUser().getId();
        final Boolean isLiked = stringRedisTemplate.opsForSet().isMember(RedisConstants.BLOG_LIKED_KEY + id, userId.toString());
        if(BooleanUtil.isTrue(isLiked)){
            final boolean updateSuc = update().setSql("liked = liked-1").eq("id", id).update();
            if(updateSuc){
                stringRedisTemplate.opsForSet().remove(RedisConstants.BLOG_LIKED_KEY+id,userId.toString());
            }
        }else {
            final boolean updateSuc = update().setSql("liked = liked+1").eq("id", id).update();
            if(updateSuc){
                stringRedisTemplate.opsForSet().add(RedisConstants.BLOG_LIKED_KEY+id,userId.toString());
            }
        }


    }

    private void setUserInfo(Blog blog){
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        isBlogLiked(blog);
    }


}

package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpStatus;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        final String token = request.getHeader("authorization");
//        if(StrUtil.isBlank(token)){
//            response.setStatus(HttpStatus.HTTP_UNAUTHORIZED);
//            return false;
//        }
        final String key = RedisConstants.LOGIN_USER_KEY + token;
        final Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        final UserDTO userDTO = BeanUtil.toBeanIgnoreError(userMap, UserDTO.class);


        //        Object user = request.getSession().getAttribute("user");
        if(Objects.nonNull(userDTO)){
            stringRedisTemplate.expire(key, Duration.ofSeconds(RedisConstants.LOGIN_USER_TTL));
        }
        UserHolder.saveUser(userDTO);
        return true ;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}

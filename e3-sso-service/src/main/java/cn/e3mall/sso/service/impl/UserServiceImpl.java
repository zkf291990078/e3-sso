package cn.e3mall.sso.service.impl;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import cn.e3mall.common.jedis.JedisClient;
import cn.e3mall.common.utils.E3Result;
import cn.e3mall.common.utils.JsonUtils;
import cn.e3mall.mapper.TbUserMapper;
import cn.e3mall.pojo.TbUser;
import cn.e3mall.pojo.TbUserExample;
import cn.e3mall.pojo.TbUserExample.Criteria;
import cn.e3mall.sso.service.UserService;

@Service
public class UserServiceImpl implements UserService {
	@Autowired
	private TbUserMapper userMapper;
	@Autowired
	private JedisClient jedisClient;
	@Value("${USER_INFO}")
	private String USER_INFO;
	@Value("${SESSION_EXPIRE}")
	private int SESSION_EXPIRE;

	@Override
	public E3Result checkUser(String param, int type) {
		// TODO Auto-generated method stub
		TbUserExample user = new TbUserExample();
		Criteria critera = user.createCriteria();
		// 2、查询条件根据参数动态生成。
		// 1、2、3分别代表username、phone、email
		if (type == 1) {
			critera.andUsernameEqualTo(param);
		} else if (type == 2) {
			critera.andPhoneEqualTo(param);
		} else if (type == 3) {
			critera.andEmailEqualTo(param);
		} else {
			return E3Result.build(400, "参数不正确");
		}
		List<TbUser> users = userMapper.selectByExample(user);
		if (users == null || users.size() == 0) {
			return E3Result.ok(true);
		}
		return E3Result.ok(false);
	}

	@Override
	public E3Result createUser(TbUser user) {
		// TODO Auto-generated method stub
		// 1、使用TbUser接收提交的请求。
		if (StringUtils.isBlank(user.getUsername())) {
			return E3Result.build(400, "用户名不能为空");
		}
		if (StringUtils.isBlank(user.getPassword())) {
			return E3Result.build(400, "密码不能为空");
		}
		// 校验数据是否可用
		E3Result result = checkUser(user.getUsername(), 1);
		if (!(boolean) result.getData()) {
			return E3Result.build(400, "此用户名已经被使用");
		}
		// 校验电话是否可以
		if (StringUtils.isNotBlank(user.getPhone())) {
			result = checkUser(user.getPhone(), 2);
			if (!(boolean) result.getData()) {
				return E3Result.build(400, "此手机号已经被使用");
			}
		}
		// 校验email是否可用
		if (StringUtils.isNotBlank(user.getEmail())) {
			result = checkUser(user.getEmail(), 3);
			if (!(boolean) result.getData()) {
				return E3Result.build(400, "此邮件地址已经被使用");
			}
		}
		// 2、补全TbUser其他属性。
		user.setCreated(new Date());
		user.setUpdated(new Date());
		// 3、密码要进行MD5加密。
		String md5Pass = DigestUtils.md5DigestAsHex(user.getPassword().getBytes());
		user.setPassword(md5Pass);
		// 4、把用户信息插入到数据库中。
		userMapper.insert(user);
		// 5、返回e3Result。
		return E3Result.ok();
	}

	@Override
	public E3Result loginUser(String param, String password) {
		// TODO Auto-generated method stub
		if (StringUtils.isBlank(param)) {
			return E3Result.build(400, "用户名不能为空");
		}
		if (StringUtils.isBlank(password)) {
			return E3Result.build(400, "密码不能为空");
		}
		TbUserExample example = new TbUserExample();
		Criteria criteria = example.createCriteria();
		criteria.andUsernameEqualTo(param);
		List<TbUser> users = userMapper.selectByExample(example);
		if (users == null && users.size() == 0) {
			return E3Result.build(400, "用户名或密码不正确");
		}
		TbUser user = users.get(0);
		String md5Pass = DigestUtils.md5DigestAsHex(password.getBytes());

		if (!md5Pass.equals(user.getPassword())) {
			return E3Result.build(400, "用户名或密码不正确");
		}

		// 2、登录成功后生成token。Token相当于原来的jsessionid，字符串，可以使用uuid。
		String token = UUID.randomUUID().toString();
		// 3、把用户信息保存到redis。Key就是token，value就是TbUser对象转换成json。
		// 4、使用String类型保存Session信息。可以使用“前缀:token”为key
		user.setPassword(null);
		jedisClient.set(USER_INFO + ":" + token, JsonUtils.objectToJson(user));
		// 5、设置key的过期时间。模拟Session的过期时间。一般半个小时。
		jedisClient.expire(USER_INFO + ":" + token, SESSION_EXPIRE);
		// 6、返回e3Result包装token。
		return E3Result.ok(token);
	}

	public E3Result getUserByToken(String token){
		String userJson= jedisClient.get(USER_INFO+":"+token);
		if(StringUtils.isBlank(userJson)){
			return E3Result.build(400, "请重新登录");
		}
		jedisClient.expire(USER_INFO+":"+token,SESSION_EXPIRE);
		TbUser user= JsonUtils.jsonToPojo(userJson, TbUser.class);
		return E3Result.ok(user);
	}

	@Override
	public E3Result logout(String token) {
		// TODO Auto-generated method stub
		jedisClient.del(USER_INFO+":"+token);
		return E3Result.ok();
	}
	
}

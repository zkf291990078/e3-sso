package cn.e3mall.sso.service;

import cn.e3mall.common.utils.E3Result;
import cn.e3mall.pojo.TbUser;

public interface UserService {

public E3Result checkUser(String param, int type);

public E3Result createUser(TbUser tbUser);

public E3Result loginUser(String param, String password);

public E3Result getUserByToken(String token);

public E3Result logout(String token);
}

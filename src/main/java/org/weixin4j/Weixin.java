/*
 * 微信公众平台(JAVA) SDK
 *
 * Copyright (c) 2014, Ansitech Network Technology Co.,Ltd All rights reserved.
 * 
 * http://www.weixin4j.org/sdk/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.weixin4j;

import com.thoughtworks.xstream.XStream;
import org.weixin4j.http.OAuth;
import org.weixin4j.http.OAuthToken;
import org.weixin4j.http.Response;
import org.weixin4j.message.Article;
import org.weixin4j.message.Articles;
import org.weixin4j.ticket.TicketType;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.weixin4j.http.Attachment;
import org.weixin4j.pay.UnifiedOrder;
import org.weixin4j.pay.UnifiedOrderResult;
import org.weixin4j.util.XStreamFactory;

/**
 * 微信平台基础支持对象
 *
 * @author weixin4j<weixin4j@ansitech.com>
 */
public class Weixin extends WeixinSupport implements java.io.Serializable {

    /**
     * 获取access_token ?grant_type=client_credential&appid=APPID&secret=APPSECRET
     */
    private static final String accessTokenURL = "https://api.weixin.qq.com/cgi-bin/token";
    /**
     * 公众号对象
     */
    protected OAuth oauth = null;
    /**
     * 公众号Token对象
     */
    protected OAuthToken oauthToken = null;

    /**
     * 微信基础支持
     */
    public Weixin() {
    }

    /**
     * 初始化微信对象
     *
     * @param accessToken 旧访问Token
     * @param appId 开发者Id
     * @param secret 开发者密钥
     * @param expiresIn 旧访问Token剩余过期时间
     * @throws WeixinException
     * @since Weixin 1.0.0
     */
    public void init(String accessToken, String appId, String secret, int expiresIn) throws WeixinException {
        if (null == accessToken || accessToken.equals("")) {
            throw new IllegalStateException("access_token is null!");
        }
        oauthToken = new OAuthToken(accessToken, expiresIn);
        oauth = new OAuth(appId, secret);
    }

    /**
     * 向微信平台发送获取access_token请求
     *
     * @param appId 第三方用户唯一凭证
     * @param secret 第三方用户唯一凭证密钥，既appsecret
     * @return 用户凭证
     * @throws WeixinException
     * @since Weixin4J 1.0.0
     */
    public OAuthToken login(String appId, String secret) throws WeixinException {
        return login(appId, secret, "client_credential");
    }

    /**
     * 向微信平台发送获取access_token请求
     *
     * @param appId 第三方用户唯一凭证
     * @param secret 第三方用户唯一凭证密钥，既appsecret
     * @param grantType 获取access_token填写client_credential
     * @return 用户凭证
     * @throws WeixinException
     * @since Weixin4J 1.0.0
     */
    public OAuthToken login(String appId, String secret, String grantType) throws WeixinException {
        if (appId == null || secret == null || appId.equals("") || secret.equals("") || grantType == null || grantType.equals("")) {
            throw new WeixinException("invalid null, appid or secret is null.");
        }
        //发送登陆请求验证，由于接口有频率限制，所以，一次请求后，在过期时间内，不在进行第二次请求
        //所以先从当前HttpClient内验证OAuthToken是否已验证，并且未过期
        if (this.oauth != null && this.oauthToken != null) {
            //判断是否过期
            if (!oauthToken.isExprexpired()) {
                //先验证用户公众号信息是否一致，不一致则需要重新登录获取
                if (this.oauth.getAppId().equals(appId) && this.oauth.getSecret().equals(secret)) {
                    //如果没有过期，则直接返回对象
                    return oauthToken;
                }
            }
        }
        //拼接参数
        String param = "?grant_type=" + grantType + "&appid=" + appId + "&secret=" + secret;
        //调用获取access_token接口
        Response res = http.get(accessTokenURL + param);
        //根据请求结果判定，是否验证成功
        JSONObject jsonObj = res.asJSONObject();
        if (jsonObj != null) {
            if (Configuration.isDebug()) {
                System.out.println("login返回json：" + jsonObj.toString());
            }
            Object errcode = jsonObj.get("errcode");
            if (errcode != null) {
                //返回异常信息
                throw new WeixinException(getCause(Integer.parseInt(errcode.toString())));
            }
            //判断是否登录成功，并判断过期时间
            Object obj = jsonObj.get("access_token");
            //登录成功，设置accessToken和过期时间
            if (obj != null) {
                //设置公众号信息
                oauth = new OAuth(appId, secret);
                //设置凭证
                this.oauthToken = (OAuthToken) JSONObject.toBean(jsonObj, OAuthToken.class);
            }
        }
        return oauthToken;
    }

    /**
     * 验证用户登录
     *
     * <p>
     * 调用所有方法之前，应先调用此方法检查用户是否已经登录，或者Token是否失效<br/>
     * 如果没有登录，则跑出异常提示登录，如果失效，密码正确的情况下回自动重新登录。</p>
     *
     * @throws WeixinException
     */
    public void checkLogin() throws WeixinException {
        //判断是否过期，如果已过期，则发送重新登录命令
        if (oauthToken == null) {
            throw new WeixinException("oauthToken is null,you must call login first!");
        } else {
            //已过期
            if (oauthToken.isExprexpired()) {
                //如果用户名和密码正确，则自动登录，否则返回异常
                if (oauth != null) {
                    //自动重新发送登录请求
                    login(oauth.getAppId(), oauth.getSecret());
                } else {
                    throw new WeixinException("oauth is null and oauthToken is exprexpired, please log in again!");
                }
            }
        }
    }

    /**
     * 获取用户基本信息
     *
     * http://mp.weixin.qq.com/wiki/14/bb5031008f1494a59c6f71fa0f319c66.html
     *
     * http请求方式: GET
     * https://api.weixin.qq.com/cgi-bin/user/info?access_token=ACCESS_TOKEN&openid=OPENID&lang=zh_CN
     */
    private static final String userInfoUrl = "https://api.weixin.qq.com/cgi-bin/user/info";

    /**
     * 根据OpenId获取用户对象
     *
     * <p>
     * 通过公众号，返回用户对象，进行用户相关操作</p>
     *
     * @param openId 普通用户的标识，对当前公众号唯一
     * @return 用户对象
     * @throws WeixinException
     */
    public User getUserInfo(String openId) throws WeixinException {
        //默认简体中文
        return getUserInfo(openId, "zh_CN");
    }

    /**
     * 获取用户对象
     *
     * <p>
     * 通过公众号，返回用户对象，进行用户相关操作</p>
     *
     * @param openId 普通用户的标识，对当前公众号唯一
     * @param lang 国家地区语言版本 zh_CN 简体，zh_TW 繁体，en 英语
     * @return 用户对象
     * @throws WeixinException
     */
    public User getUserInfo(String openId, String lang) throws WeixinException {
        //必须先调用检查登录方法
        checkLogin();
        //拼接参数
        String param = "?access_token=" + this.oauthToken.getAccess_token() + "&openid=" + openId + "&lang=" + lang;
        //调用获取access_token接口
        Response res = http.get(userInfoUrl + param);
        //根据请求结果判定，是否验证成功
        JSONObject jsonObj = res.asJSONObject();
        if (jsonObj != null) {
            if (Configuration.isDebug()) {
                System.out.println("getUserInfo返回json：" + jsonObj.toString());
            }
            Object errcode = jsonObj.get("errcode");
            if (errcode != null) {
                //返回异常信息
                throw new WeixinException(getCause(Integer.parseInt(errcode.toString())));
            }
            //设置公众号信息
            return (User) JSONObject.toBean(jsonObj, User.class);
        }
        return null;
    }
    /**
     * 获取关注者列表
     *
     * http://mp.weixin.qq.com/wiki/index.php?title=获取关注者列表
     *
     * http请求方式: GET（请使用https协议）
     * https://api.weixin.qq.com/cgi-bin/user/get?access_token=ACCESS_TOKEN&next_openid=NEXT_OPENID
     */
    private static final String userGetUrl = "https://api.weixin.qq.com/cgi-bin/user/get";

    /**
     * 获取所有关注者列表
     *
     * <p>
     * 通过公众号，返回用户对象，进行用户相关操作</p>
     *
     * @return 关注者对象
     * @throws WeixinException
     */
    public Followers getAllUserList() throws WeixinException {
        Followers allFollower = new Followers();
        Data data = new Data();
        data.setOpenid(new ArrayList<String>());
        allFollower.setData(data);
        String next_openid = "";
        do {
            Followers f = getUserList(next_openid);
            if (f == null) {
                break;
            }
            if (f.getCount() > 0) {
                List<String> openids = f.getData().getOpenid();
                for (String openid : openids) {
                    allFollower.getData().getOpenid().add(openid);
                }
            }
            next_openid = f.getNext_openid();
        } while (!next_openid.equals(""));
        return allFollower;
    }

    /**
     * 获取关注者列表
     *
     * <p>
     * 通过公众号，返回用户对象，进行用户相关操作</p>
     *
     * @param next_openid 第一个拉取的OPENID，不填默认从头开始拉取
     * @return 关注者对象
     * @throws WeixinException when Weixin service or network is unavailable, or
     * the user has not authorized
     */
    public Followers getUserList(String next_openid) throws WeixinException {
        //拼接参数
        String param = "?access_token=" + this.oauthToken.getAccess_token() + "&next_openid=";
        //第一次获取不添加参数
        if (next_openid != null && !next_openid.equals("")) {
            param += next_openid;
        }
        //调用获取access_token接口
        Response res = http.get(userGetUrl + param);
        //根据请求结果判定，是否验证成功
        JSONObject jsonObj = res.asJSONObject();
        Followers follower = null;
        if (jsonObj != null) {
            if (Configuration.isDebug()) {
                System.out.println("getUserList返回json：" + jsonObj.toString());
            }
            Object errcode = jsonObj.get("errcode");
            if (errcode != null) {
                //返回异常信息
                throw new WeixinException(getCause(Integer.parseInt(errcode.toString())));
            }
            Map<String, Class> map = new HashMap<String, Class>();
            map.put("data", Data.class);
            follower = (Followers) JSONObject.toBean(jsonObj, Followers.class, map);
        }
        return follower;
    }
    /**
     * 创建分组
     *
     * http请求方式: POST（请使用https协议）
     * https://api.weixin.qq.com/cgi-bin/groups/create?access_token=ACCESS_TOKEN
     * POST数据格式：json POST数据例子：{"group":{"name":"test"}}
     */
    private static final String groupsCreateUrl = "https://api.weixin.qq.com/cgi-bin/groups/create";

    /**
     * 创建分组
     *
     * @param name 分组名字（30个字符以内）
     * @return 创建成功，返回带Id的Group对象
     * @throws WeixinException 创建分组异常
     */
    public Group createGroup(String name) throws WeixinException {
        //必须先调用检查登录方法
        checkLogin();
        //内部业务验证
        if (name == null || name.equals("")) {
            throw new IllegalStateException("name is null!");
        }
        //拼接参数
        JSONObject postGroup = new JSONObject();
        JSONObject postName = new JSONObject();
        postName.put("name", name);
        postGroup.put("group", postName);
        //调用获取access_token接口
        Response res = http.post(groupsCreateUrl + "?access_token=" + this.oauthToken.getAccess_token(), postGroup);
        //根据请求结果判定，是否验证成功
        JSONObject jsonObj = res.asJSONObject();
        Group group = null;
        if (jsonObj != null) {
            if (Configuration.isDebug()) {
                System.out.println("createGroup返回json：" + jsonObj.toString());
            }
            Object errcode = jsonObj.get("errcode");
            if (errcode != null) {
                //返回异常信息
                throw new WeixinException(getCause(Integer.parseInt(errcode.toString())));
            }
            JSONObject jsonGroup = jsonObj.getJSONObject("group");
            group = (Group) JSONObject.toBean(jsonGroup, Group.class);
        }
        return group;
    }
    /**
     * 查询所有分组
     *
     * http请求方式: GET（请使用https协议）
     * https://api.weixin.qq.com/cgi-bin/groups/get?access_token=ACCESS_TOKEN
     */
    private static final String groupsGetUrl = "https://api.weixin.qq.com/cgi-bin/groups/get";

    /**
     * 查询所有分组
     *
     * <p>
     * 最多支持创建500个分组</p>
     *
     * @return 分组列表
     * @throws WeixinException 查询所有分组异常
     */
    public List<Group> getGroups() throws WeixinException {
        //必须先调用检查登录方法
        checkLogin();
        List<Group> groupList = new ArrayList<Group>();
        //调用获取access_token接口
        Response res = http.post(groupsGetUrl + "?access_token=" + this.oauthToken.getAccess_token(), null);
        //根据请求结果判定，是否验证成功
        JSONObject jsonObj = res.asJSONObject();
        if (jsonObj != null) {
            if (Configuration.isDebug()) {
                System.out.println("getGroups返回json：" + jsonObj.toString());
            }
            Object errcode = jsonObj.get("errcode");
            if (errcode != null) {
                //返回异常信息
                throw new WeixinException(getCause(Integer.parseInt(errcode.toString())));
            }
            //获取分组列表
            JSONArray groups = jsonObj.getJSONArray("groups");
            for (int i = 0; i < groups.size(); i++) {
                JSONObject jsonGroup = groups.getJSONObject(i);
                Group group = (Group) JSONObject.toBean(jsonGroup, Group.class);
                groupList.add(group);
            }
        }
        return groupList;
    }
    /**
     * 查询用户所在分组
     *
     * http请求方式: POST（请使用https协议）
     * https://api.weixin.qq.com/cgi-bin/groups/getid?access_token=ACCESS_TOKEN
     * POST数据格式：json POST数据例子：{"openid":"od8XIjsmk6QdVTETa9jLtGWA6KBc"}
     */
    private static final String groupsGetIdUrl = "https://api.weixin.qq.com/cgi-bin/groups/getid";

    /**
     * 查询用户所在分组
     *
     * <p>
     * 通过用户的OpenID查询其所在的GroupID</p>
     *
     * @param openid 用户唯一标识符
     * @return 返回用户所在分组Id
     * @throws WeixinException 查询用户所在分组异常
     */
    public int getGroupId(String openid) throws WeixinException {
        //必须先调用检查登录方法
        checkLogin();
        //内部业务验证
        if (openid == null || openid.equals("")) {
            throw new IllegalStateException("openid is null!");
        }
        int groupId = -1;
        //拼接参数
        JSONObject postParam = new JSONObject();
        postParam.put("openid", openid);
        //调用获取access_token接口
        Response res = http.post(groupsGetIdUrl + "?access_token=" + this.oauthToken.getAccess_token(), postParam);
        //根据请求结果判定，是否验证成功
        JSONObject jsonObj = res.asJSONObject();
        if (jsonObj != null) {
            if (Configuration.isDebug()) {
                System.out.println("getGroupId返回json：" + jsonObj.toString());
            }
            Object errcode = jsonObj.get("errcode");
            if (errcode != null) {
                //返回异常信息
                throw new WeixinException(getCause(Integer.parseInt(errcode.toString())));
            }
            //获取成功返回分组Id
            groupId = jsonObj.getInt("groupid");
        }
        return groupId;
    }
    /**
     * 修改分组名
     *
     * http请求方式: POST（请使用https协议）
     * https://api.weixin.qq.com/cgi-bin/groups/update?access_token=ACCESS_TOKEN
     * POST数据格式：json POST数据例子：{"group":{"id":108,"name":"test2_modify2"}}
     */
    private static final String groupsUpdateUrl = "https://api.weixin.qq.com/cgi-bin/groups/update";

    /**
     * 修改分组名
     *
     * @param id 分组id，由微信分配
     * @param name 分组名字（30个字符以内）
     * @throws WeixinException 修改分组名异常
     */
    public void updateGroup(int id, String name) throws WeixinException {
        //必须先调用检查登录方法
        checkLogin();
        //内部业务验证
        if (id < 0) {
            throw new IllegalStateException("id can not <= 0!");
        }
        if (name == null || name.equals("")) {
            throw new IllegalStateException("name is null!");
        }
        //拼接参数
        JSONObject postGroup = new JSONObject();
        JSONObject postName = new JSONObject();
        postName.put("id", id);
        postName.put("name", name);
        postGroup.put("group", postName);
        //调用获取access_token接口
        Response res = http.post(groupsUpdateUrl + "?access_token=" + this.oauthToken.getAccess_token(), postGroup);
        //根据请求结果判定，是否验证成功
        JSONObject jsonObj = res.asJSONObject();
        if (jsonObj != null) {
            if (Configuration.isDebug()) {
                System.out.println("updateGroup返回json：" + jsonObj.toString());
            }
            //判断是否修改成功
            //正常时返回 {"errcode": 0, "errmsg": "ok"}
            //错误时返回 示例：{"errcode":40013,"errmsg":"invalid appid"}
            int errcode = jsonObj.getInt("errcode");
            //登录成功，设置accessToken和过期时间
            if (errcode != 0) {
                //返回异常信息
                throw new WeixinException(getCause(errcode));
            }
        }
    }

    /**
     * 删除分组
     *
     * http请求方式: POST（请使用https协议）
     * https://api.weixin.qq.com/cgi-bin/groups/delete?access_token=ACCESS_TOKEN
     * 对应删除接口，正确的Json返回结果: {"errcode":0,"errmsg":"ok"}
     */
    private static final String groupDeleteUrl = "https://api.weixin.qq.com/cgi-bin/groups/delete";

    /**
     * 删除分组
     *
     * @param groupId 分组Id
     * @throws WeixinException 删除分组异常
     */
    public void deleteGroup(int groupId) throws WeixinException {
        //必须先调用检查登录方法
        checkLogin();
        //拼接参数
        JSONObject postParam = new JSONObject();
        JSONObject group = new JSONObject();
        group.put("id", groupId);
        postParam.put("group", group);
        //调用获取access_token接口
        Response res = http.post(groupDeleteUrl + "?access_token=" + this.oauthToken.getAccess_token(), postParam);
        //根据请求结果判定，是否验证成功
        JSONObject jsonObj = res.asJSONObject();
        if (jsonObj != null) {
            if (Configuration.isDebug()) {
                System.out.println("deleteGroup返回json：" + jsonObj.toString());
            }
            Object errcode = jsonObj.get("errcode");
            if (errcode != null && !errcode.toString().equals("0")) {
                //返回异常信息
                throw new WeixinException(getCause(Integer.parseInt(errcode.toString())));
            }
        }
    }

    /**
     * 移动用户分组
     *
     * http请求方式: POST（请使用https协议）
     * https://api.weixin.qq.com/cgi-bin/groups/members/update?access_token=ACCESS_TOKEN
     * POST数据格式：json
     * POST数据例子：{"openid":"oDF3iYx0ro3_7jD4HFRDfrjdCM58","to_groupid":108}
     */
    private static final String groupsMembersUpdateUrl = "https://api.weixin.qq.com/cgi-bin/groups/members/update";

    /**
     * 移动用户分组
     *
     * @param openid 用户唯一标识符
     * @param to_groupid 分组id
     * @throws WeixinException 移动用户分组异常
     */
    public void updateMemberGroup(String openid, int to_groupid) throws WeixinException {
        //必须先调用检查登录方法
        checkLogin();
        //内部业务验证
        if (openid == null || openid.equals("")) {
            throw new IllegalStateException("openid is null!");
        }
        if (to_groupid < 0) {
            throw new IllegalStateException("to_groupid can not <= 0!");
        }
        //拼接参数
        JSONObject postParam = new JSONObject();
        postParam.put("openid", openid);
        postParam.put("to_groupid", to_groupid);
        //调用获取access_token接口
        Response res = http.post(groupsMembersUpdateUrl + "?access_token=" + this.oauthToken.getAccess_token(), postParam);
        //根据请求结果判定，是否验证成功
        JSONObject jsonObj = res.asJSONObject();
        if (jsonObj != null) {
            if (Configuration.isDebug()) {
                System.out.println("updateMemberGroup返回json：" + jsonObj.toString());
            }
            //判断是否修改成功
            //正常时返回 {"errcode": 0, "errmsg": "ok"}
            //错误时返回 示例：{"errcode":40013,"errmsg":"invalid appid"}
            int errcode = jsonObj.getInt("errcode");
            //登录成功，设置accessToken和过期时间
            if (errcode != 0) {
                //返回异常信息
                throw new WeixinException(getCause(errcode));
            }
        }
    }

    /**
     * 自定义菜单
     *
     * http请求方式: POST（请使用https协议）
     * https://api.weixin.qq.com/cgi-bin/menu/create?access_token=ACCESS_TOKEN
     * POST数据格式：json POST数据例子：{ "button":[ { "type":"click", "name":"今日歌曲",
     * "key":"V1001_TODAY_MUSIC" }, { "type":"click", "name":"歌手简介",
     * "key":"V1001_TODAY_SINGER" }, { "name":"菜单", "sub_button":[ {
     * "type":"view", "name":"搜索", "url":"http://www.soso.com/" }, {
     * "type":"view", "name":"视频", "url":"http://v.qq.com/" }, { "type":"click",
     * "name":"赞一下我们", "key":"V1001_GOOD" }] }] }
     */
    private static final String menuCreateUrl = "https://api.weixin.qq.com/cgi-bin/menu/create";

    /**
     * 创建自定义菜单
     *
     * @param menu 菜单对象
     * @throws WeixinException 创建自定义菜单异常
     */
    public void createMenu(Menu menu) throws WeixinException {
        //必须先调用检查登录方法
        checkLogin();
        //内部业务验证
        if (menu == null || menu.getButton() == null) {
            throw new IllegalStateException("menu is null!");
        }
        //调用获取access_token接口
        Response res = http.post(menuCreateUrl + "?access_token=" + this.oauthToken.getAccess_token(), menu.toJSONObject());
        //根据请求结果判定，是否验证成功
        JSONObject jsonObj = res.asJSONObject();
        if (jsonObj != null) {
            if (Configuration.isDebug()) {
                System.out.println("createMenu返回json：" + jsonObj.toString());
            }
            Object errcode = jsonObj.get("errcode");
            if (errcode != null && !errcode.toString().equals("0")) {
                //返回异常信息
                throw new WeixinException(getCause(Integer.parseInt(errcode.toString())));
            }
        }
    }
    /**
     * 获取自定义菜单json字符串
     *
     * http请求方式: POST（请使用https协议）
     * https://api.weixin.qq.com/cgi-bin/menu/get?access_token=ACCESS_TOKEN
     * POST数据格式：json
     * POST数据例子：{"menu":{"button":[{"type":"click","name":"今日歌曲","key":"V1001_TODAY_MUSIC","sub_button":[]},{"type":"click","name":"歌手简介","key":"V1001_TODAY_SINGER","sub_button":[]},{"name":"菜单","sub_button":[{"type":"view","name":"搜索","url":"http://www.soso.com/","sub_button":[]},{"type":"view","name":"视频","url":"http://v.qq.com/","sub_button":[]},{"type":"click","name":"赞一下我们","key":"V1001_GOOD","sub_button":[]}]}]}}
     */
    private static final String menuGetUrl = "https://api.weixin.qq.com/cgi-bin/menu/get";

    /**
     * 查询自定义菜单
     *
     * <p>
     * 最多支持创建2级自定义菜单</p>
     *
     * @return 自定义菜单对象
     * @throws WeixinException 查询自定义菜单对象异常
     */
    public Menu getMenu() throws WeixinException {
        //必须先调用检查登录方法
        checkLogin();
        //调用获取access_token接口
        Response res = http.post(menuGetUrl + "?access_token=" + this.oauthToken.getAccess_token(), null);
        //根据请求结果判定，是否验证成功
        JSONObject jsonObj = res.asJSONObject();
//        JSONObject jsonObj = JSONObject.fromObject("{\"menu\": {\"button\": [{\"type\": \"view\", \"name\": \"最新活动\", \"url\": \"http://wap.tiexinqiao.net\", \"sub_button\": [ ]}, {\"type\": \"click\", \"name\": \"特价优惠\", \"key\": \"C_1\", \"sub_button\": [ ]}, {\"name\": \"我的生活\", \"sub_button\": [{\"type\": \"click\", \"name\": \"生活超市\", \"key\": \"C_2_1\", \"sub_button\": [ ]}, {\"type\": \"click\", \"name\": \"网上订餐\", \"key\": \"C_2_2\", \"sub_button\": [ ]}, {\"type\": \"click\", \"name\": \"我的优惠劵\", \"key\": \"C_2_3\", \"sub_button\": [ ]}]}]}}");
        if (jsonObj != null) {
            if (Configuration.isDebug()) {
                System.out.println("getMenu返回json：" + jsonObj.toString());
            }
            Object errcode = jsonObj.get("errcode");
            if (errcode != null) {
                //返回异常信息
                throw new WeixinException(getCause(Integer.parseInt(errcode.toString())));
            }
            //返回自定义菜单对象
            return new Menu(jsonObj);
        }
        //返回自定义菜单对
        return null;
    }
    /**
     * 自定义菜单删除接口
     *
     * http请求方式：GET
     * https://api.weixin.qq.com/cgi-bin/menu/delete?access_token=ACCESS_TOKEN
     * 对应删除接口，正确的Json返回结果: {"errcode":0,"errmsg":"ok"}
     */
    private static final String menuDeleteUrl = "https://api.weixin.qq.com/cgi-bin/menu/delete";

    /**
     * 删除自定义菜单
     *
     * @throws WeixinException 删除自定义菜单异常
     */
    public void deleteMenu() throws WeixinException {
        //必须先调用检查登录方法
        checkLogin();
        //调用获取access_token接口
        Response res = http.get(menuDeleteUrl + "?access_token=" + this.oauthToken.getAccess_token());
        //根据请求结果判定，是否验证成功
        JSONObject jsonObj = res.asJSONObject();
        if (jsonObj != null) {
            if (Configuration.isDebug()) {
                System.out.println("deleteMenu返回json：" + jsonObj.toString());
            }
            Object errcode = jsonObj.get("errcode");
            if (errcode != null && !errcode.toString().equals("0")) {
                //返回异常信息
                throw new WeixinException(getCause(Integer.parseInt(errcode.toString())));
            }
        }
    }

    /**
     * 生成带参数的二维码 - 创建二维码ticket
     *
     * http请求方式：GET
     * https://api.weixin.qq.com/cgi-bin/qrcode/create?access_token=TOKEN
     * 对应删除接口，正确的Json返回结果: {"errcode":0,"errmsg":"ok"}
     */
    private static final String qrcodeCreateUrl = "https://api.weixin.qq.com/cgi-bin/qrcode/create";
    /**
     * 生成带参数的二维码 - 通过ticket换取二维码
     *
     * HTTP GET请求（请使用https协议）
     * https://mp.weixin.qq.com/cgi-bin/showqrcode?ticket=TICKET
     * 提醒：TICKET记得进行UrlEncode
     */
    private static final String showqrcodeUrl = "https://mp.weixin.qq.com/cgi-bin/showqrcode";

    /**
     * 创建二维码ticket
     *
     * @param ticketType 二维码类型
     * @param fileName 图片文件路径
     * @param scene_id 场景值ID
     * @param expire_seconds 临时二维码过期时间
     * @throws WeixinException
     */
    public void createQrcode(TicketType ticketType, String fileName, int scene_id, int expire_seconds) throws WeixinException {
        //必须先调用检查登录方法
        checkLogin();
        //内部业务验证
        if (ticketType.equals(TicketType.QR_SCENE)) {
            if (scene_id <= 0) {
                throw new IllegalStateException("场景id不能小于等于0");
            }
        } else if (ticketType.equals(TicketType.QR_LIMIT_SCENE)) {
            if (scene_id <= 0 || scene_id > 100000) {
                throw new IllegalStateException("永久场景id参数只支持1-100000");
            }
        }
        JSONObject ticketJson = new JSONObject();
        if (ticketType.equals(TicketType.QR_SCENE)) {
            //临时二维码过期时间
            ticketJson.put("expire_seconds", expire_seconds);
        }
        //二维码类型
        ticketJson.put("action_name", ticketType.toString());

        JSONObject actionInfo = new JSONObject();
        JSONObject scene = new JSONObject();
        scene.put("scene_id", scene_id);
        actionInfo.put("scene", scene);
        //二维码详细信息
        ticketJson.put("action_info", actionInfo);

        //调用创建Tick的access_token接口
        Response res = http.post(qrcodeCreateUrl + "?access_token=" + this.oauthToken.getAccess_token(), ticketJson);
        //根据请求结果判定，返回结果
        JSONObject jsonObj = res.asJSONObject();
        if (jsonObj != null) {
            if (Configuration.isDebug()) {
                System.out.println("createQrcode返回json：" + jsonObj.toString());
            }
            Object errcode = jsonObj.get("errcode");
            if (errcode != null && !errcode.toString().equals("0")) {
                //返回异常信息
                throw new WeixinException(getCause(Integer.parseInt(errcode.toString())));
            } else {
                try {
                    //通过ticket换取二维码
                    URL url = new URL(showqrcodeUrl + "?ticket=" + jsonObj.getString("ticket"));
                    // 打开连接
                    URLConnection con = url.openConnection();
                    // 输入流
                    InputStream is = con.getInputStream();
                    // 1K的数据缓冲
                    byte[] bs = new byte[1024];
                    // 读取到的数据长度
                    int len;
                    // 输出的文件流
                    OutputStream os = new FileOutputStream(fileName);
                    // 开始读取
                    while ((len = is.read(bs)) != -1) {
                        os.write(bs, 0, len);
                    }
                    // 完毕，关闭所有链接
                    os.close();
                    is.close();
                } catch (MalformedURLException ex) {
                    throw new WeixinException("通过ticket换取二维码异常：", ex);
                } catch (IOException ex) {
                    throw new WeixinException("通过ticket换取二维码，下载二维码图片异常：", ex);
                }
            }
        }
    }
    /**
     * 根据OpenID列表群发 http请求方式: POST
     * https://api.weixin.qq.com/cgi-bin/message/mass/send?access_token=ACCESS_TOKEN
     */
    private static final String massSendUrl = "https://api.weixin.qq.com/cgi-bin/message/mass/send";

    /**
     * 根据OpenID列表群发文本消息
     *
     * @param openIds 粉丝OpenId集合
     * @param txtContent 文本消息内容
     * @return 发送成功则返回群发消息Id，否则返回null
     * @throws org.weixin4j.WeixinException
     */
    public String massSendContent(String[] openIds, String txtContent) throws WeixinException {
        JSONObject json = new JSONObject();
        JSONObject text = new JSONObject();
        text.put("content", txtContent);
        json.put("touser", openIds);
        json.put("text", text);
        json.put("msgtype", "text");
        Response res = http.post(massSendUrl + "?access_token=" + this.oauthToken.getAccess_token(), json);
        //根据请求结果判定，是否验证成功
        JSONObject jsonObj = res.asJSONObject();
        if (jsonObj != null) {
            if (Configuration.isDebug()) {
                System.out.println("群发文本消息返回json：" + jsonObj.toString());
            }
            Object errcode = jsonObj.get("errcode");
            if (errcode != null && !errcode.toString().equals("0")) {
                //返回异常信息
                throw new WeixinException(getCause(Integer.parseInt(errcode.toString())));
            } else {
                //返回群发消息id
                return jsonObj.getString("msg_id");
            }
        }
        return null;
    }

    /**
     * 根据OpenID列表群发文本消息
     *
     * @param openIds 粉丝OpenId集合
     * @param mediaId 图文消息素材Id
     * @return 发送成功则返回群发消息Id，否则返回null
     * @throws WeixinException
     */
    public String massSendNews(String[] openIds, String mediaId) throws WeixinException {
        JSONObject json = new JSONObject();
        JSONObject media_id = new JSONObject();
        media_id.put("media_id", mediaId);
        json.put("touser", openIds);
        json.put("mpnews", media_id);
        json.put("msgtype", "mpnews");
        Response res = http.post(massSendUrl + "?access_token=" + this.oauthToken.getAccess_token(), json);
        //根据请求结果判定，是否验证成功
        JSONObject jsonObj = res.asJSONObject();
        if (jsonObj != null) {
            if (Configuration.isDebug()) {
                System.out.println("群发图文消息返回json：" + jsonObj.toString());
            }
            Object errcode = jsonObj.get("errcode");
            if (errcode != null && !errcode.toString().equals("0")) {
                //返回异常信息
                throw new WeixinException(getCause(Integer.parseInt(errcode.toString())));
            } else {
                //返回群发图文id
                return jsonObj.getString("msg_id");
            }
        }
        return null;
    }

    /**
     * http请求方式: POST
     * http://file.api.weixin.qq.com/cgi-bin/media/upload?access_token=ACCESS_TOKEN&type=TYPE
     */
    private static final String mediaUploadUrl = "http://file.api.weixin.qq.com/cgi-bin/media/upload";

    /**
     * 上传多媒体文件
     *
     * @param type 媒体文件类型，分别有图片（image）、语音（voice）、视频（video）和缩略图（thumb）
     * @param file form-data中媒体文件标识，有filename、filelength、content-type等信息
     * @return 上传成功返回素材Id，否则返回null
     * @throws WeixinException
     */
    public String uploadMedia(String type, File file) throws WeixinException {
        try {
            String jsonStr = http.upload(mediaUploadUrl + "?access_token=" + this.oauthToken.getAccess_token() + "&type=" + type, file);
            JSONObject jsonObj = JSONObject.fromObject(jsonStr);
            if (jsonObj != null) {
                if (Configuration.isDebug()) {
                    System.out.println("上传多媒体文件返回json：" + jsonObj.toString());
                }
                Object errcode = jsonObj.get("errcode");
                if (errcode != null && !errcode.toString().equals("0")) {
                    //返回异常信息
                    throw new WeixinException(getCause(Integer.parseInt(errcode.toString())));
                } else {
                    //返回多媒体文件id
                    return jsonObj.getString("media_id");
                }
            }
            return null;
        } catch (Exception ex) {
            throw new WeixinException("上传多媒体文件异常:", ex);
        }
    }

    /**
     * http请求方式: POST
     * https://api.weixin.qq.com/cgi-bin/media/uploadnews?access_token=ACCESS_TOKEN
     */
    private static final String mediaUploadnewsUrl = "https://api.weixin.qq.com/cgi-bin/media/uploadnews";

    /**
     * 上传图文消息素材
     *
     * @param articles 图文消息，一个图文消息支持1到10条图文
     * @return 上传成功返回图文素材Id，否则返回null
     * @throws WeixinException
     */
    public String uploadnews(List<Article> articles) throws WeixinException {
        JSONObject json = new JSONObject();
        json.put("articles", articles);
        Response res = http.post(mediaUploadnewsUrl + "?access_token=" + this.oauthToken.getAccess_token(), json);
        //根据请求结果判定，是否验证成功
        JSONObject jsonObj = res.asJSONObject();
        if (jsonObj != null) {
            if (Configuration.isDebug()) {
                System.out.println("uploadnews返回json：" + jsonObj.toString());
            }
            Object errcode = jsonObj.get("errcode");
            if (errcode != null && !errcode.toString().equals("0")) {
                //返回异常信息
                throw new WeixinException(getCause(Integer.parseInt(errcode.toString())));
            } else {
                //返回图文消息id
                return jsonObj.getString("media_id");
            }
        }
        return null;
    }

    /**
     * http请求方式: POST
     * https://api.weixin.qq.com/cgi-bin/message/custom/send?access_token=ACCESS_TOKEN
     */
    private static final String customSendUrl = "https://api.weixin.qq.com/cgi-bin/message/custom/send";

    /**
     * 发送客服文本消息
     *
     * @param openId 粉丝OpenId
     * @param txtContent 文本消息内容
     * @throws org.weixin4j.WeixinException
     */
    public void customSendContent(String openId, String txtContent) throws WeixinException {
        JSONObject json = new JSONObject();
        JSONObject text = new JSONObject();
        text.put("content", txtContent);
        json.put("touser", openId);
        json.put("text", text);
        json.put("msgtype", "text");
        http.post(customSendUrl + "?access_token=" + this.oauthToken.getAccess_token(), json);
    }

    /**
     * 发送客服图文消息
     *
     * @param openId 粉丝OpenId
     * @param articles 图文消息，一个图文消息支持1到10条图文
     * @throws org.weixin4j.WeixinException
     */
    public void customSendNews(String openId, List<Articles> articles) throws WeixinException {
        JSONObject json = new JSONObject();
        json.put("touser", openId);
        json.put("msgtype", "news");
        JSONObject news = new JSONObject();
        news.put("articles", articles);
        json.put("news", news);
        http.post(customSendUrl + "?access_token=" + this.oauthToken.getAccess_token(), json);
    }

    /**
     * 下载多媒体文件 http请求方式: GET
     * http://file.api.weixin.qq.com/cgi-bin/media/get?access_token=ACCESS_TOKEN&media_id=MEDIA_ID
     */
    private static final String mediaGetUrl = "http://file.api.weixin.qq.com/cgi-bin/media/get";

    /**
     * 下载多媒体文件
     *
     * @param mediaId 媒体文件ID
     * @return 正确返回附件对象，否则返回null
     * @throws IOException
     */
    public Attachment download(String mediaId) throws IOException {
        //下载资源
        String url = mediaGetUrl + "?access_token=" + this.oauthToken.getAccess_token() + "&media_id=" + mediaId;
        return http.download(url);
    }

    /**
     * 获取jsapi_ticket
     *
     * 作用：公众号用于调用微信JS接口的临时票据
     *
     * jsapi_ticket的有效期为7200秒
     * https://api.weixin.qq.com/cgi-bin/ticket/getticket?access_token=ACCESS_TOKEN&type=jsapi
     */
    private static final String jsapiGetTicketUrl = "https://api.weixin.qq.com/cgi-bin/ticket/getticket";

    /**
     * 获取jsapi_ticket
     *
     * @return 成功返回ticket，失败返回NULL
     * @throws WeixinException
     */
    public String getJsApiTicket() throws WeixinException {
        //必须先调用检查登录方法
        checkLogin();
        //调用获取jsapi_ticket接口
        Response res = http.get(jsapiGetTicketUrl + "?access_token=" + this.oauthToken.getAccess_token() + "&type=jsapi");
        //根据请求结果判定，是否验证成功
        JSONObject jsonObj = res.asJSONObject();
        //成功返回如下JSON:
        //{"errcode":0,"errmsg":"ok","ticket":"bxLdikRXVbTPdHSM05e5u5sUoXNKd8-41ZO3MhKoyN5OfkWITDGgnr2fwJ0m9E8NYzWKVZvdVtaUgWvsdshFKA","expires_in":7200}
        if (jsonObj != null) {
            if (Configuration.isDebug()) {
                System.out.println("获取jsapi_ticket返回json：" + jsonObj.toString());
            }
            Object errcode = jsonObj.get("errcode");
            if (errcode != null && !errcode.toString().equals("0")) {
                //返回异常信息
                throw new WeixinException(getCause(Integer.parseInt(errcode.toString())));
            } else {
                Object ticket = jsonObj.get("ticket");
                if (ticket != null) {
                    return ticket.toString();
                }
            }
        }
        return null;
    }

    /**
     * 统一下单
     */
    private static final String payUnifiedOrderURL = "https://api.mch.weixin.qq.com/pay/unifiedorder";

    /**
     * 统一下单
     *
     * @param unifiedorder 统一下单对象
     * @return 下单返回结果对象
     * @throws org.weixin4j.WeixinException
     */
    public UnifiedOrderResult payUnifiedOrder(UnifiedOrder unifiedorder) throws WeixinException {
        //将统一下单对象转成XML
        String xmlPost = unifiedorder.toXML();
        if (Configuration.isDebug()) {
            System.out.println("调试模式_统一下单接口 提交XML数据：" + xmlPost);
        }
        //提交xml格式数据
        Response res = http.postXml(payUnifiedOrderURL, xmlPost);
        //获取微信平台下单接口返回数据
        String xmlResult = res.asString();
        //将返回结果，转换为统一下单返回结果对象
        XStream xsIn = XStreamFactory.init(false);
        //设置根对象
        xsIn.alias("xml", UnifiedOrderResult.class);
        //返回结果
        return (UnifiedOrderResult) xsIn.fromXML(xmlResult);
    }
}
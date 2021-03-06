package com.peaceful.auth.sdk.Impl;

import com.alibaba.fastjson.JSON;
import com.peaceful.auth.sdk.api.AuthService;
import com.peaceful.auth.sdk.api.CacheService;
import com.peaceful.auth.sdk.conf.OpenParse;
import com.peaceful.auth.sdk.conf.SDKConf;
import com.peaceful.auth.sdk.exception.CreateSessionException;
import com.peaceful.auth.data.util.VCS;
import com.peaceful.auth.data.domain.*;
import com.peaceful.auth.data.response.Response;
import com.peaceful.auth.sdk.other.Constant;
import com.peaceful.auth.sdk.util.HttpUtils;
import com.peaceful.auth.sdk.util.LoadPropertiesException;
import com.peaceful.common.util.Http;
import net.sf.ehcache.Element;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


/**
 * Created by wangjun on 14-4-23.
 */
public class AuthServiceImpl implements com.peaceful.auth.sdk.api.AuthService {
    final private static Logger LOGGER = LoggerFactory.getLogger(AuthServiceImpl.class);
    final public static CacheService CACHE_SERVICE = new CacheServiceImpl();
    private static SDKConf publicServiceURL = null;
    static final int UNINITIALIZED = 0;
    static final int SUCCESSFUL_INITIALIZATION = 1;
    static int INITIALIZATION_STATE = UNINITIALIZED;


    public List<String> login(String email, String password) {
        List<String> roleNameList =  new ArrayList<String>();
        if (identificationEmail(email, password)) {
            Http.getRequest().getSession().setAttribute(Constant.CURRENT_USER, email);
            List<JSONRole> list = getRolesOfSystem();
            for (JSONRole role: list) {
                roleNameList.add(role.name);
            }
        }
        return roleNameList;
    }

    public void logout() {
        Http.getRequest().getSession().removeAttribute(Constant.CURRENT_USER);
    }

    /**
     * 取得服务
     *
     * @return
     */
    public static AuthService getAuthService() throws CreateSessionException {
        if (INITIALIZATION_STATE == SUCCESSFUL_INITIALIZATION) {
            return SingletonHolder.authService;
        }
        LOGGER.info("-----------------------------------------------------------------");
        LOGGER.info("init auth service");
        LOGGER.info("-----------------------------------------------------------------");
        try {
            publicServiceURL = new SDKConf();
        } catch (LoadPropertiesException e) {
            INITIALIZATION_STATE = UNINITIALIZED;
            throw new CreateSessionException("未能载入需要的配置文件auth.properties，有关配置信息请访问：" + SDKConf.create_session_exception);
        }
        JSONSystem system = OpenParse.getSystemInfo();
        if (system == null) {
            INITIALIZATION_STATE = UNINITIALIZED;
            return null;
        } else {
            INITIALIZATION_STATE = SUCCESSFUL_INITIALIZATION;
            SDKConf.system_id = String.valueOf(system.id);
            SDKConf.token = system.token;
            return SingletonHolder.authService;
        }
    }

    private static class SingletonHolder {
        private static AuthService authService = new AuthServiceImpl();

    }

    private AuthServiceImpl() {
        LOGGER.info(formatOut());
        LOGGER.info(publicServiceURL.success_init_info);
    }

    //得到服务端数据版本号
    private String getVCSNum() {
        Element element = (Element) CACHE_SERVICE.get("vcs");
        String vcsNum = null;
        if (element != null)
            vcsNum = (String) element.getObjectValue();
        if (StringUtils.isEmpty(vcsNum)) {
            vcsNum = HttpUtils.get(publicServiceURL.find_vcs + "?systemId=" + SDKConf.system_id + "&token=" + SDKConf.token);
            CACHE_SERVICE.put("vcs", vcsNum, Integer.parseInt(publicServiceURL.client_cache_valid_time));
        }
        return vcsNum;
    }

    public JSONSystem getSystem() {
        Element element = null;
        String vcsNum = getVCSNum();
        if (vcsNum.compareTo("" + VCS.getCurrentVersion()) <= 0)
            element = (Element) CACHE_SERVICE.get("system");
        else {
            CACHE_SERVICE.clearAll();
            VCS.setCurrentVersion(Long.parseLong(vcsNum));
        }
        JSONSystem system = null;
        if (element == null) {
            String result = HttpUtils.get(publicServiceURL.system_all_info + "?systemId=" + SDKConf.system_id + "&token=" + SDKConf.token);
            try {
                system = JSON.parseObject(result, JSONSystem.class);
                CACHE_SERVICE.put("system", system, 686868);
            } catch (Exception e) {
                LOGGER.error("解析数据错误:{}", e);
            }
        } else {
            system = (JSONSystem) element.getObjectValue();
        }
        return system;
    }


    public JSONUser getUser(String email) {
        Element element = null;
        String vcsNum = getVCSNum();
        if (vcsNum.compareTo("" + VCS.getCurrentVersion()) <= 0) {
            element = (Element) CACHE_SERVICE.get(email);
        } else {
            CACHE_SERVICE.clearAll();
            VCS.setCurrentVersion(Long.parseLong(vcsNum));
        }
        JSONUser user = null;
        if (element == null) {
            String result = HttpUtils.get(publicServiceURL.user_info + email + "&token=" + SDKConf.token);
            boolean flag = true;
            if ("null".equals(result)) {
                clearSession(email);
                flag = false;
            }
            if (flag) {
                try {
                    user = JSON.parseObject(result, JSONUser.class);
                    LOGGER.debug("{} has auth  is {}", email, result);
                    CACHE_SERVICE.put(email, user, 686868);
                } catch (Exception e) {
                    LOGGER.error("数据解析错误:\n" + e);
                }
            }
        } else {
            user = (JSONUser) element.getObjectValue();
        }
        return user;
    }

    @Deprecated
    public String getMenu(String menukey, String attribute, int type, String email) {
        JSONUser user = getUser(email);
        List<JSONMenu> menus = user.menus;
        JSONMenu res_menu = null;
        String res = null;
        for (JSONMenu menu : menus) {
            if (menu.menukey.equals(menukey)) {
                res_menu = menu;
            }
        }
        if (res_menu != null) {
            if (type == 1) {
                res = "<button " + attribute + ">" + res_menu.name + "</button>";
            } else if (type == 2) {
                res = "<a href='" + res_menu.url + "'>" + res_menu.name + "</a>";
            } else if (type == 3) {
                res = "<a " + attribute + ">" + res_menu.name + "</a>";
            }
        } else {
            if (type == 4) {
                res = "<button " + attribute + ">" + "<span style='color'>" + res_menu.name + "</span></button>";
            } else if (type == 5) {
                res = "<a " + attribute + ">" + "<span style='color'>" + res_menu.name + "</span></a>";
            }
        }
        return res;
    }

    public JSONMenu getMenu(String menukey, String email) {
        JSONUser user = getUser(email);
        List<JSONMenu> menus = user.menus;
        JSONMenu res_menu = null;
        for (JSONMenu menu : menus) {
            if (menu.menukey.equals(menukey)) {
                res_menu = menu;
            }
        }
        return res_menu;
    }

    public boolean requestCheck(String url, String email) {
        boolean flag = false;
        JSONUser user;
        user = getUser(email);
        List<JSONResource> resources = user.resources;
        Iterator<JSONResource> iterator = resources.iterator();
        while (iterator.hasNext()) {
            JSONResource resource = iterator.next();
            String pattern = resource.pattern;
            if (url.indexOf(pattern) != -1) {
                flag = true;
                break;
            }
        }
        return flag;
    }

    public void clearSession(String email) {
        CACHE_SERVICE.clear(email);
    }


    public boolean identificationEmail(String email, String password) {
        Map<String, String> data = new HashMap<String, String>();
        data.put("email", email);
        data.put("password", password);
        data.put("systemId", publicServiceURL.system_id);
        data.put("token", SDKConf.token);
        return ("true".equals(HttpUtils.post(publicServiceURL.identification_email, data)) ? true : false);
    }


    //*********************下面这些方法是供那些需要在自己的系统里的去配置权限的系统调用************************/

    public Response insertUser(JSONUser user) {
        Map<String, String> data = new HashMap<String, String>();
        data.put("email", user.email);
        data.put("name", user.name);
        data.put("operator", user.operator);
        data.put("systemId", publicServiceURL.system_id);
        if (user.roles != null) {
            int i = 0;
            for (JSONRole role : user.roles) {
                data.put("roleIds[" + i + "]", String.valueOf(role.getId()));
                i++;
            }
        }
        return JSON.parseObject(HttpUtils.post(publicServiceURL.insert_user, data), Response.class);
    }

    @Override
    public Response updateUser(JSONUser user, boolean cascade_user, Integer[] roleIds) {
        if (user.id == null)
            return new Response(0, 0, "ERROR", "用户id不可以为空");
        Map<String, String> data = new HashMap<String, String>();
        data.put("email", user.email);
        data.put("id", String.valueOf(user.id));
        data.put("name", user.name);
        data.put("passwordState", String.valueOf(user.passwordState));
        data.put("password", user.getPassword());
        data.put("cascade_user", String.valueOf(cascade_user));
        data.put("operator", user.operator);
        data.put("isdel", String.valueOf(user.isdel));
        data.put("systemId", publicServiceURL.system_id);
        if (cascade_user) {
            if (roleIds != null) {
                data.put("roleIds", JSON.toJSONString(roleIds));
            }
        }
        return JSON.parseObject(HttpUtils.post(publicServiceURL.update_user, data), Response.class);
    }

    @Override
    public Response insertRole(JSONRole role) {
        Map<String, String> data = new HashMap<String, String>();
        data.put("name", role.name);
        data.put("description", role.description);
        data.put("operator", role.operator);
        data.put("systemId", publicServiceURL.system_id);
        return JSON.parseObject(HttpUtils.post(publicServiceURL.insert_role, data), Response.class);
    }

    @Override
    public Response updateRole(JSONRole role, boolean cascade_menu, Integer[] menuIds) {
        Map<String, String> data = new HashMap<String, String>();
        data.put("id", String.valueOf(role.id));
        data.put("name", role.name);
        data.put("cascade_menu", String.valueOf(cascade_menu));
        data.put("isdel", String.valueOf(role.isdel));
        data.put("description", role.description);
        data.put("operator", role.operator);
        data.put("systemId", publicServiceURL.system_id);
        if (cascade_menu) {
            if (menuIds != null) {
                data.put("menuIds", JSON.toJSONString(menuIds));
            }
        }
        return JSON.parseObject(HttpUtils.post(publicServiceURL.update_role, data), Response.class);

    }

    @Override
    public List<JSONMenu> getMenusOfSystem() {
        return JSON.parseArray(HttpUtils.get(publicServiceURL.find_menus), JSONMenu.class);
    }

    @Override
    public List<JSONRole> getRolesOfSystem() {
        return JSON.parseArray(HttpUtils.get(publicServiceURL.find_roles + "&token=" + SDKConf.token), JSONRole.class);

    }

    @Override
    public List<JSONUser> getUsersOfSystem() {
        return JSON.parseArray(HttpUtils.get(publicServiceURL.find_users), JSONUser.class);

    }

    @Override
    public List<JSONResource> getResourcesOfSystem() {
        return JSON.parseArray(HttpUtils.get(publicServiceURL.find_resources), JSONResource.class);
    }

    private static String formatOut() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("成功载入配置文件：app.id=");
        buffer.append(publicServiceURL.system_id);
        buffer.append(",service.address=");
        buffer.append(publicServiceURL.service_address);
        buffer.append(",user.session.out.time=");
        buffer.append(publicServiceURL.user_session_out_time);
        buffer.append(",system.session.out.time=");
        buffer.append(publicServiceURL.system_session_out_time);
        return buffer.toString();
    }
}

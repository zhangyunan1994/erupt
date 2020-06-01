package xyz.erupt.auth.service;

import com.google.gson.reflect.TypeToken;
import eu.bitwalker.useragentutils.UserAgent;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.erupt.annotation.EruptField;
import xyz.erupt.annotation.sub_field.sub_edit.VL;
import xyz.erupt.auth.base.LoginModel;
import xyz.erupt.auth.constant.SessionKey;
import xyz.erupt.auth.interceptor.LoginInterceptor;
import xyz.erupt.auth.model.EruptMenu;
import xyz.erupt.auth.model.EruptUser;
import xyz.erupt.auth.model.log.EruptLoginLog;
import xyz.erupt.auth.repository.UserRepository;
import xyz.erupt.auth.util.IpUtil;
import xyz.erupt.auth.util.MD5Utils;
import xyz.erupt.core.view.EruptApiModel;
import xyz.erupt.core.view.EruptModel;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * @author liyuepeng
 * @date 2018-12-13.
 */
@Service
public class EruptUserService {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private HttpServletRequest request;

    @Transactional
    public void saveLoginLog(EruptUser user) {
        UserAgent userAgent = UserAgent.parseUserAgentString(request.getHeader("User-Agent"));
        EruptLoginLog loginLog = new EruptLoginLog();
        loginLog.setEruptUser(user);
        loginLog.setLoginTime(new Date());
        loginLog.setIp(IpUtil.getIpAddr(request));
        loginLog.setSystemName(userAgent.getOperatingSystem().getName());
        loginLog.setRegion(IpUtil.getCityInfo(loginLog.getIp()));
        loginLog.setBrowser(userAgent.getBrowser().getName() + " " + (userAgent.getBrowserVersion() == null ? "" : userAgent.getBrowserVersion().getMajorVersion()));
        loginLog.setDeviceType(userAgent.getOperatingSystem().getDeviceType().getName());
        entityManager.persist(loginLog);
    }

    public static final String LOGIN_ERROR_HINT = "账号或密码错误";

    public LoginModel login(String account, String pwd, String verifyCode, HttpServletRequest request) {
        Object loginError = sessionService.get(SessionKey.LOGIN_ERROR + account);
        long loginErrorCount = 0;
        if (null != loginError) {
            loginErrorCount = Long.parseLong(loginError.toString());
        }
        if (loginErrorCount >= 3) {
            if (StringUtils.isBlank(verifyCode)) {
                return new LoginModel(false, "请填写验证码", true);
            }
            Object vc = sessionService.get(SessionKey.VERIFY_CODE + account);
            sessionService.remove(SessionKey.VERIFY_CODE + account);
            if (vc == null || !vc.toString().equalsIgnoreCase(verifyCode)) {
                return new LoginModel(false, "验证码不正确", true);
            }
        }
        EruptUser eruptUser = userRepository.findByAccount(account);
        if (null != eruptUser) {
            if (!eruptUser.getStatus()) {
                return new LoginModel(false, "账号已锁定!");
            }
            //校验IP
            if (StringUtils.isNotBlank(eruptUser.getWhiteIp())) {
                boolean isAllowIp = false;
                String ipAddr = IpUtil.getIpAddr(request);
                for (String ip : eruptUser.getWhiteIp().split("\n")) {
                    if (ip.equals(ipAddr)) {
                        isAllowIp = true;
                        break;
                    }
                }
                if (!isAllowIp) {
                    return new LoginModel(false, "ip不允许访问");
                }
            }
            //校验密码
            boolean pass = false;
            {
                String digestPwd;
                if (eruptUser.getIsMd5()) {
                    digestPwd = eruptUser.getPassword();
                } else {
                    digestPwd = MD5Utils.digest(eruptUser.getPassword());
                }
                String calcPwd = MD5Utils.digest(digestPwd +
                        Calendar.getInstance().get(Calendar.DAY_OF_MONTH) + account);
                if (pwd.equalsIgnoreCase(calcPwd)) {
                    pass = true;
                }
            }
            if (pass) {
                sessionService.put(SessionKey.LOGIN_ERROR + account, "0");
                return new LoginModel(true, eruptUser);
            } else {
                loginErrorCount += 1;
                sessionService.put(SessionKey.LOGIN_ERROR + account, loginErrorCount + "");
                if (loginErrorCount >= 3) {
                    return new LoginModel(false, LOGIN_ERROR_HINT, true);
                } else {
                    return new LoginModel(false, LOGIN_ERROR_HINT);
                }
            }
        } else {
            return new LoginModel(false, LOGIN_ERROR_HINT);
        }
    }

    @Transactional
    public EruptApiModel changePwd(String account, String pwd, String newPwd, String newPwd2) {
        if (!newPwd.equals(newPwd2)) {
            return EruptApiModel.errorNoInterceptApi("修改失败，新密码与确认密码不匹配");
        }
        EruptUser eruptUser = userRepository.findByAccount(account);
        if (eruptUser.getIsMd5()) {
            pwd = MD5Utils.digest(pwd);
            newPwd = MD5Utils.digest(newPwd);
        }
        if (eruptUser.getPassword().equals(pwd)) {
            if (newPwd.equals(eruptUser.getPassword())) {
                return EruptApiModel.errorNoInterceptApi("修改失败，新密码不能和原始密码一样");
            }
            eruptUser.setPassword(newPwd);
            entityManager.merge(eruptUser);
            return EruptApiModel.successApi();
        } else {
            return EruptApiModel.errorNoInterceptApi("密码错误");
        }
    }


    public void createToken(LoginModel loginModel) {
        loginModel.setToken(RandomStringUtils.random(20, true, true));
        sessionService.put(SessionKey.USER_TOKEN + loginModel.getToken(), loginModel.getEruptUser().getId().toString());
    }

    private static VL[] VLS = {};

    public EruptUser getCurrentEruptUser() {
        entityManager.clear();
        return entityManager.find(EruptUser.class, getCurrentUid());
    }

    public boolean verifyToken(String token) {
        if (null == sessionService.get(SessionKey.USER_TOKEN + token)) {
            return false;
        } else {
            return true;
        }
    }

    public Long getCurrentUid() {
        String token = request.getHeader(LoginInterceptor.ERUPT_HEADER_TOKEN);
        if (StringUtils.isBlank(token)) {
            token = request.getParameter(LoginInterceptor.URL_ERUPT_PARAM_TOKEN);
        }
        Object uid = sessionService.get(SessionKey.USER_TOKEN + token);
        if (null != uid) {
            return Long.valueOf(uid.toString());
        } else {
            throw new RuntimeException("登录过期请重新登录");
        }
    }

    static {
        try {
            VLS = EruptMenu.class.getDeclaredField("path")
                    .getAnnotation(EruptField.class).edit().inputType().prefix();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    public boolean verifyMenuAuth(String token, String name) {
        List<EruptMenu> menus = sessionService.get(SessionKey.MENU_LIST + token, new TypeToken<List<EruptMenu>>() {
        }.getType());
        for (EruptMenu menu : menus) {
            if (StringUtils.isNotBlank(menu.getPath())
                    && !EruptMenu.DISABLE.equals(menu.getStatus().toString())
                    && menu.getPath().toLowerCase().contains(name.toLowerCase())) {
                String path = menu.getPath();
                for (VL vl : VLS) {
                    if (vl.value().length() > 2 && path.contains(vl.value())) {
                        path = menu.getPath().replace(vl.value(), "");
                        break;
                    }
                }
                if (path.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean verifyEruptMenuAuth(String token, String authStr, EruptModel eruptModel) {
        //校验authStr与请求头erupt信息是否匹配，来验证其合法性
        if (!authStr.equalsIgnoreCase(eruptModel.getEruptName())) {
            return false;
        }
        //检验注解
        if (!eruptModel.getErupt().loginUse()) {
            return true;
        }
        //校验菜单权限
        {
            return verifyMenuAuth(token, eruptModel.getEruptName());
        }
    }

}

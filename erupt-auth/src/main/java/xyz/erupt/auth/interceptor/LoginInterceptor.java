package xyz.erupt.auth.interceptor;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import xyz.erupt.annotation.sub_erupt.RowOperation;
import xyz.erupt.auth.config.EruptAuthConfig;
import xyz.erupt.auth.model.EruptUser;
import xyz.erupt.auth.model.log.EruptOperateLog;
import xyz.erupt.auth.service.EruptUserService;
import xyz.erupt.auth.util.IpUtil;
import xyz.erupt.core.annotation.EruptApi;
import xyz.erupt.core.annotation.EruptRouter;
import xyz.erupt.core.service.EruptCoreService;
import xyz.erupt.core.view.EruptFieldModel;
import xyz.erupt.core.view.EruptModel;

import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.Transactional;
import java.util.Date;

/**
 * @author liyuepeng
 * @date 2019-12-05.
 */
@Service
public class LoginInterceptor extends HandlerInterceptorAdapter {

    private static final String REQ_DATE = "@req_date@";
    private static final String REQ_BODY = "@req_body@";
    @Autowired
    private EruptUserService eruptUserService;

    //header
    private static final String ERUPT_HEADER_KEY = "erupt";

    public static final String ERUPT_HEADER_TOKEN = "token";

    private static final String ERUPT_PARENT_HEADER_KEY = "eruptParent";

    //param
    private static final String URL_ERUPT_PARAM_KEY = "_erupt";

    private static final String ERUPT_PARENT_PARAM_KEY = "_eruptParent";

    public static final String URL_ERUPT_PARAM_TOKEN = "_token";
    @Autowired
    private EruptAuthConfig eruptAuthConfig;
    @Autowired
    private EntityManager entityManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        request.setAttribute(REQ_DATE, System.currentTimeMillis());
        EruptRouter eruptRouter = null;
        if (handler instanceof HandlerMethod) {
            eruptRouter = ((HandlerMethod) handler).getMethodAnnotation(EruptRouter.class);
        }
        if (null == eruptRouter) {
            return true;
        }
        String token = null;
        String eruptName = null;
        String parentEruptName = null;
        if (eruptRouter.verifyMethod() == EruptRouter.VerifyMethod.HEADER) {
            token = request.getHeader(ERUPT_HEADER_TOKEN);
            eruptName = request.getHeader(ERUPT_HEADER_KEY);
            parentEruptName = request.getHeader(ERUPT_PARENT_HEADER_KEY);
        } else if (eruptRouter.verifyMethod() == EruptRouter.VerifyMethod.PARAM) {
            token = request.getParameter(URL_ERUPT_PARAM_TOKEN);
            eruptName = request.getParameter(URL_ERUPT_PARAM_KEY);
            parentEruptName = request.getHeader(ERUPT_PARENT_PARAM_KEY);
        }
        if (null == token || !eruptUserService.verifyToken(token)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
        String path = request.getServletPath();
        //权限校验
        switch (eruptRouter.verifyType()) {
            case LOGIN:
                break;
            case MENU:
                if (!eruptUserService.verifyMenuAuth(token, eruptName)) {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.sendError(HttpStatus.FORBIDDEN.value());
                    return false;
                }
                break;
            case ERUPT:
                EruptModel eruptModel = EruptCoreService.getErupt(eruptName);
                if (null == eruptModel) {
                    response.setStatus(HttpStatus.NOT_FOUND.value());
                    return false;
                }
                String authStr = path.split("/")[eruptRouter.skipAuthIndex() + eruptRouter.authIndex() + 1];
                //eruptParent logic
                $ep:
                if (StringUtils.isNotBlank(parentEruptName)) {
                    EruptModel eruptParentModel = EruptCoreService.getErupt(parentEruptName);
                    for (EruptFieldModel model : eruptParentModel.getEruptFieldModels()) {
                        if (eruptModel.getEruptName().equals(model.getFieldReturnName())) {
                            if (authStr.equals(eruptModel.getEruptName())) {
                                authStr = eruptParentModel.getEruptName();
                            }
                            eruptModel = eruptParentModel;
                            break $ep;
                        }
                    }
                    for (RowOperation operation : eruptParentModel.getErupt().rowOperation()) {
                        if (operation.eruptClass().getSimpleName().equals(eruptModel.getEruptName())) {
                            break $ep;
                        }
                    }
                    response.setStatus(HttpStatus.NOT_FOUND.value());
                    return false;
                }
                if (!path.contains(eruptName) || !eruptUserService.verifyEruptMenuAuth(token, authStr, eruptModel)) {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.sendError(HttpStatus.FORBIDDEN.value());
                    return false;
                }
                break;
        }
        return true;
    }


    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    @Transactional
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (eruptAuthConfig.isRecordOperateLog()) {
            if (handler instanceof HandlerMethod) {
                HandlerMethod handlerMethod = (HandlerMethod) handler;
                EruptApi eruptApi = handlerMethod.getMethodAnnotation(EruptApi.class);
                EruptRouter eruptRouter = handlerMethod.getMethodAnnotation(EruptRouter.class);
                if (null != eruptApi && eruptApi.value()) {
                    EruptOperateLog operate = new EruptOperateLog();
                    if (null != eruptRouter && eruptRouter.verifyType() == EruptRouter.VerifyType.ERUPT) {
                        String eruptName;
                        if (eruptRouter.verifyMethod() == EruptRouter.VerifyMethod.HEADER) {
                            eruptName = request.getHeader(ERUPT_HEADER_KEY);
                        } else {
                            eruptName = request.getParameter(URL_ERUPT_PARAM_KEY);
                        }
                        operate.setApiName(eruptApi.desc() + " —— " + EruptCoreService.getErupt(eruptName).getErupt().name());
                    } else {
                        operate.setApiName(eruptApi.desc());
                    }
                    operate.setIp(IpUtil.getIpAddr(request));
                    operate.setRegion(IpUtil.getCityInfo(operate.getIp()));
                    operate.setStatus(true);
                    operate.setReqMethod(handlerMethod.toString());
//                    operate.setReqParam(request.getAttribute(REQ_BODY).toString());
                    operate.setReqAddr(request.getRequestURL().toString());
                    operate.setEruptUser(new EruptUser() {{
                        this.setId(eruptUserService.getCurrentUid());
                    }});
                    Date date = new Date();
                    operate.setCreateTime(date);
                    operate.setTotalTime(date.getTime() - (Long) request.getAttribute(REQ_DATE));
                    if (null != ex) {
                        operate.setErrorInfo(ExceptionUtils.getStackTrace(ex));
                        operate.setStatus(false);
                    }
                    entityManager.persist(operate);
                }
            }
        }
    }


    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        super.afterConcurrentHandlingStarted(request, response, handler);
    }
}

package org.activiti.web.simple.webapp.controller;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.activiti.engine.IdentityService;
import org.activiti.web.simple.webapp.service.AccountService;
import org.activiti.web.simple.webapp.service.ActivitiWorkFlowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.crypto.codec.Base64;

import com.xzg.dao.ActivitiWorkflowLogin;
import com.xzg.domain.Authority;
import com.xzg.domain.Node;
import com.xzg.domain.User;
import com.xzg.listener.SessionListener;
import com.xzg.publicUtil.CookieUtil;

@Controller
public class AccountController {
	//日志信息
		protected Logger logger = LoggerFactory.getLogger(getClass());
	@Resource
	private IdentityService identityService;
	@Resource(name="accountServiceImpl")
	private AccountService accountService;
	@Resource(name="activitiWorkFlowServiceImpl")
	private ActivitiWorkFlowService activitiWorkFlowService;
	//修改自定义用户
	@Resource(name="ActivitiWorkflowLoginImple")
	private ActivitiWorkflowLogin activitiWorkflowLogin;
	ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception{
		return null;
	}
	/**
	 * 跳转到登录页面
	 * @return
	 */
	//spring核心过滤器，为返回的字符串添加.jsp
	@RequestMapping(value="/login.do",method={RequestMethod.GET,RequestMethod.GET})
	public String login(){
		return "views/login";
	}
	/**
	 * 跳转到登录页面
	 * @return
	 */
	//spring核心过滤器，为返回的字符串添加.jsp
	@RequestMapping(value="/loginAuto.do",method={RequestMethod.GET,RequestMethod.GET})
	public String login(HttpServletRequest request,RedirectAttributes redirectAttributes){
		String forword="";
		String userId="";//等同于前台的username
		String password="";
		boolean isLogin = false;
		User user;
		//使用cookie自动登录
		String cookieValue=CookieUtil.getUid(request, "cn.xzg");
		if(cookieValue!=null){
			//base64解密
			String cookiedeBase64=new String(Base64.decode(cookieValue.getBytes()));
			//通过：来拆分存到string数组中
			String[] cookieArry = cookiedeBase64.split(":");
			//拆分后的值
			/* cookieArry[0] ---- 用户名 cookieArry[1] ---- cookie有效时间 cookieArry[2] ---- MD5明文字符串*/
			if(cookieArry.length!=3){
				isLogin=false;
				}else{
					long effictTime = Long.valueOf(cookieArry[1]);
					userId= cookieArry[0];
					String	cookieMd5Cli=cookieArry[2];
						if(effictTime<System.currentTimeMillis()){
							isLogin=false;
							logger.debug("======cookie有效时间过期！===========");
						}else{
							user = activitiWorkflowLogin.getUserInfo(userId);
							if(user!=null){
								password=user.getPassword();
								String cookieMd5Ser = CookieUtil.getMD5(userId+password+CookieUtil.webKey);
								//
								if(cookieMd5Cli.equals(cookieMd5Ser)){
									//验证成功自动登录
									isLogin=true;
									logger.debug("==============cookie可以自动登录！=========");
								}else{
									//用户信息不匹配阻止登录
									isLogin=false;
									logger.debug("用户信息和数据库中不符！");
								}
							}
					}
				}
		}
//要根据判断结果来确定是否登录
		if(isLogin){
			user=activitiWorkflowLogin.getUserInfo(userId);
			List<com.xzg.domain.Group> listGroup  = activitiWorkflowLogin.getUserOfGroup(userId);
			request.getSession().setAttribute("loginuser", user);
			request.getSession().setAttribute("listGroup", listGroup);
			//listener加入session的属性中：开始监听
			SessionListener sessionListener = new SessionListener(request.getServletContext());
			//获取sessionId保存到全局变量application中
			 ServletContext application = request.getServletContext();
			 //获取保运的用户id
			 String appId = (String) application.getAttribute("userid");
			 //先判断session会话是否已存在全局变量中
			if(userId.equals(appId)){
				redirectAttributes.addFlashAttribute("message", "您已登录，请不要重复登录!");
				forword="/login.do";//login.jsp
			}else{
					application.setAttribute("userid", userId);
				 	request.getSession().setAttribute("sessionListener", sessionListener);
					redirectAttributes.addFlashAttribute("message", "登录成功!");
					forword="/main.do";//main.jsp
			}
		}else{
			redirectAttributes.addFlashAttribute("message", "用户名或密码错误!");
			//登录失败，在login_tmp表中更新字段num-1直到为0时锁定用户（5分钟内）当锁定用户时禁止登录
			forword="/login.do";//login.jsp
		}
	return "redirect:"+forword;
		}
	/**
	 * 退出
	 * @return
	 */
	@RequestMapping(value="/loginout.do",method={RequestMethod.POST,RequestMethod.GET})
	public String loginout(HttpSession session,HttpServletRequest request){
		//在session中删除当前的用户信息
		session.removeAttribute("loginuser");
		//是否需要移除HttpSessionBindLinstener,监听移除
		session.removeAttribute("sessionListener");
		session.invalidate();//终止会话
		return "redirect:/login.do";
	}
	/**
	 * 跳转到主页面
	 * @return
	 */
	@RequestMapping(value="/main.do",method={RequestMethod.POST,RequestMethod.GET})
	public String main(){
		return "views/main";
	}
	/**
	 * 执行用户登录
	 * @param username接受表单提交过来的用户名
	 * @param password接受表单提交过来的密码
	 * @param request
	 * @param response
	 * @return
	 */
	@RequestMapping(value="/loginin.do",method={RequestMethod.POST,RequestMethod.GET})
	public String loginin(@RequestParam("username")String userid,@RequestParam("password")String password,HttpServletRequest request, HttpServletResponse response,RedirectAttributes redirectAttributes){
		String forword="";
		if((userid!=null&&userid.length()>0)&&(password!=null&&password.length()>0)){
			password=CookieUtil.getMD5(password);//加密用户密码
			boolean b = accountService.checkPassword(userid, password);
			if(b){
			com.xzg.domain.User	user = activitiWorkflowLogin.getUserInfo(userid);
				//com.lin.domain.User user = activitiWorkFlowService.getUserInfo(username);
				user.setUserId(Integer.valueOf(userid));
				user.setPassword(password);
				//查询用户所在的组
				//List<Group> listGroup = identityService.createGroupQuery().groupMember(userid).list();
				List<com.xzg.domain.Group> listGroup  = activitiWorkflowLogin.getUserOfGroup(userid);
				request.getSession().setAttribute("loginuser", user);
				request.getSession().setAttribute("listGroup", listGroup);
				//listener加入session的属性中：开始监听
				SessionListener sessionListener = new SessionListener(request.getServletContext());
				//获取sessionId保存到全局变量application中
				 ServletContext application = request.getServletContext();
				 //获取保运的用户id
				 String appId = (String) application.getAttribute("userid");
				 //先判断session会话是否已存在全局变量中
				if(userid.equals(appId)){
					redirectAttributes.addFlashAttribute("message", "您已登录，请不要重复登录!");
					forword="/login.do";//login.jsp
				}else{
						application.setAttribute("userid", userid);
					 	request.getSession().setAttribute("sessionListener", sessionListener);
						redirectAttributes.addFlashAttribute("message", "登录成功!");
						forword="/main.do";//main.jsp
				}
			}else{
				redirectAttributes.addFlashAttribute("message", "用户名或密码错误!");
				//登录失败，在login_tmp表中更新字段num-1直到为0时锁定用户（5分钟内）当锁定用户时禁止登录
				forword="/login.do";//login.jsp
			}
		}else{
			forword="/login.do";//login.jsp
			redirectAttributes.addFlashAttribute("message", "用户名或密码不能为空!");
		}
		return "redirect:"+forword;
	}
	/**
	 * 跳转到用户管理页面
	 * @return
	 */
	@RequestMapping(value="/userwork.do",method={RequestMethod.POST,RequestMethod.GET})
	public String userwork(){
		return "views/user/userwork";
	}
	/**
	 * 跳转到用户管理页面
	 * @return
	 */
	@RequestMapping(value="/groupwork.do",method={RequestMethod.POST,RequestMethod.GET})
	public String groupwork(){
		return "views/group/groupwork";
	}
	
	/**
	 * 查看用户列表
	 * @param request
	 * @param response
	 * @return
	 */
	@RequestMapping(value="/userlist.do",method={RequestMethod.POST,RequestMethod.GET})
	public ModelAndView userlist(HttpServletRequest request, HttpServletResponse response){
		//List<org.activiti.engine.identity.User> listuser = accountService.createUserQuery().list();
		List<com.xzg.domain.User	> listuser = accountService.getUserList();
		ModelAndView modelAndView=new ModelAndView();
		modelAndView.setViewName("views/user/listuser");
		modelAndView.addObject("listuser", listuser);
		return modelAndView;
	}
	
	
	/**
	 * 查看组员列表
	 * @param request
	 * @param response
	 * @return
	 */
	@RequestMapping(value="/grouplist.do",method={RequestMethod.POST,RequestMethod.GET})
	public ModelAndView grouplist(HttpServletRequest request, HttpServletResponse response){
		//List<Group> listgroup = accountService.createGroupQuery().list();
		List<com.xzg.domain.Group> listgroup = accountService.getListGroup();
		ModelAndView modelAndView=new ModelAndView();
		modelAndView.setViewName("views/group/listgroup");
		modelAndView.addObject("listgroup", listgroup);
		return modelAndView;
	}
	/**
	 * 查看组内的成员
	 * @param request
	 * @param response
	 * @return
	 */
	@RequestMapping(value="/memberofgroup/{groupId}.do",method={RequestMethod.POST,RequestMethod.GET})
	public ModelAndView memberOfGroup(@PathVariable("groupId")String groupId){
		//List<User> listMemberOfGroupUser = identityService.createUserQuery().memberOfGroup(groupId).list();
		List<com.xzg.domain.User> listMemberOfGroupUser = activitiWorkflowLogin.memberOfGroup(groupId);
		ModelAndView modelAndView=new ModelAndView();
		modelAndView.setViewName("views/user/listuser");
		modelAndView.addObject("listuser", listMemberOfGroupUser);
		return modelAndView;
	}
	/**
	 * 根据用户id删除用户
	 * @param request
	 * @param response
	 * @return 
	 */
	@RequestMapping(value="/{Id}/deleteUserById.do" ,method={RequestMethod.POST,RequestMethod.GET})
	public  String deleteUserById(@PathVariable("Id") String userId,RedirectAttributes redirectAttributes){
		String message="";
		try{
			accountService.deleteUser(userId);
			message="删除用户成功！";
		}catch(Exception e){
			message="删除用户失败！";
		}
		redirectAttributes.addFlashAttribute("message", message);
		return "redirect:/userlist.do";//重定向到用户管理界面
	}
	
	@RequestMapping(value="/showUpdateUserById.do",method={RequestMethod.POST,RequestMethod.GET})
	@ResponseBody
	public Map<String,Object> showUpdateByid(@RequestBody  Map<String, String> map){
		String userid;
    	if(map.containsKey("id")){
    		userid=map.get("id");
    	}else{
    		userid="";
    	}
		Map<String,Object> mapout = new HashMap<String, Object>();
		com.xzg.domain.User user = activitiWorkflowLogin.getUserInfo(userid);
		mapout.put("user",user);
		return mapout;
	}
	
	@RequestMapping(value="/showUpdateGroupById.do",method={RequestMethod.POST,RequestMethod.GET})
	@ResponseBody
	public Map<String,Object> showUpdateGroupById(@RequestBody  Map<String, String> map){
		String roleId;
    	if(map.containsKey("id")){
    		roleId=map.get("id");
    	}else{
    		roleId="";
    	}
		Map<String,Object> mapout = new HashMap<String, Object>();
		com.xzg.domain.Group group = activitiWorkflowLogin.getGroupInfo(roleId);
		mapout.put("group",group);
		return mapout;
	}
	//@使用ResponseBody表示传递json数据，所以以下转发时数据当做json数据处理
	@RequestMapping(value="/updateUserById.do",method={RequestMethod.POST,RequestMethod.GET})
	public String updateUserById(com.xzg.domain.User user,RedirectAttributes redirectAttributes){
		String message="";
		try{
			activitiWorkflowLogin.updateUserByid(user);
			message="修改用户成功！";
		}catch(Exception e){
			message="修改用户失败！";
		}
		redirectAttributes.addFlashAttribute("message", message);
		return"redirect:/userlist.do";//重定向到用户管理界面
	}
	
	@RequestMapping(value="/updateGroupById.do",method={RequestMethod.POST,RequestMethod.GET})
	public String updateGroupById(com.xzg.domain.Group group,RedirectAttributes redirectAttributes){
		String message="";
		try{
			activitiWorkflowLogin.updateGroupByid(group);
			message="修改角色成功！";
		}catch(Exception e){
			message="修改角色失败！";
		}
		redirectAttributes.addFlashAttribute("message", message);
		return"redirect:/grouplist.do";//重定向到用户管理界面
	}
@RequestMapping(value="/deleteGroupById/${roleId}.do",method=RequestMethod.POST)
	public String deleteGroupById(@PathVariable("roleId")String roleId,RedirectAttributes redirectAttributes){
	String message="";
	try{
		accountService.deleteGroup(roleId);
		message="删除角色成功！";
	}catch(Exception e){
		message="删除角色失败！";
	}
	redirectAttributes.addFlashAttribute("message", message);
	return "redirect:/grouplist.do";//重定向到用户管理界面
}
@ResponseBody
@RequestMapping(value="/treeList.do",method={RequestMethod.POST})
public List<Node> treeList(HttpServletRequest request, HttpServletResponse response){
	com.xzg.domain.User user = (com.xzg.domain.User)request.getSession().getAttribute("loginuser");
	List<Node> tree=activitiWorkflowLogin.treeList(user);
	return tree;
}
@RequestMapping(value="/authority.do",method={RequestMethod.GET})
public ModelAndView authorityList(){
	List<Authority> authoritys = activitiWorkflowLogin.authorityList();
	ModelAndView modelAndView=new ModelAndView();
	modelAndView.setViewName("views/authority/authority");
	modelAndView.addObject("authoritys", authoritys);
	return modelAndView;
}
@RequestMapping(value="updateAuthorityById.do",method={RequestMethod.POST})
public String updateAuthorityById(Authority authority,RedirectAttributes redirectAttributes){
	String msg="";
	try{
		activitiWorkflowLogin.updateAuthorityById(authority);
		msg="修改成功！";
	}catch(Exception e){
		msg="修改失败！";
	}
	redirectAttributes.addFlashAttribute("message", msg);
	return "redirect:/authority.do"; 
}
@RequestMapping(value="/showUpdateAuthorityById.do",method={RequestMethod.POST,RequestMethod.GET})
@ResponseBody
public Map<String,Object> showUpdateAuthorityById(@RequestBody  Map<String, String> map){
	String authorityId;
	if(map.containsKey("id")){
		authorityId=map.get("id");
	}else{
		authorityId="";
	}
	Map<String,Object> mapout = new HashMap<String, Object>();
	Authority authority = activitiWorkflowLogin.selectAuthorityById(authorityId);
	mapout.put("authority",authority);
	return mapout;
}
//保存cookie自动登录
@RequestMapping(value="/saveCookie.do",method={RequestMethod.POST,RequestMethod.GET})
@ResponseBody
public void saveCookie(@RequestBody  Map<String, String> map,HttpServletRequest request, HttpServletResponse response){
	if(map.containsKey("username")&&map.containsKey("password")){
	String	username=map.get("username");
	String	password = map.get("password");
	//保存
	CookieUtil.saveCookie(username, password,response);
	}else{
		logger.error("未获取到用户姓名和密码！");
	}
}
/**利用MD5进行加密
 * @param str  待加密的字符串
 * @return  加密后的字符串
 * @throws NoSuchAlgorithmException  没有这种产生消息摘要的算法
 * @throws UnsupportedEncodingException  
 */
public String EncoderByMd5(String str) throws NoSuchAlgorithmException, UnsupportedEncodingException{
	String newStr="";
	try {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] md5 = md.digest(str.getBytes());
        newStr=md5.toString();
    } catch (Exception e) {
    	logger.error("加密失败！");
        throw new RuntimeException(e);
        
    }
    //加密后的字符串
	return newStr;
	}
}

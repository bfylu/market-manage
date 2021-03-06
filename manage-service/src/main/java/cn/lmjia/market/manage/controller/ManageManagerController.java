package cn.lmjia.market.manage.controller;

import cn.lmjia.market.core.converter.QRController;
import cn.lmjia.market.core.entity.Login;
import cn.lmjia.market.core.entity.Manager;
import cn.lmjia.market.core.entity.support.ManageLevel;
import cn.lmjia.market.core.repository.LoginRepository;
import cn.lmjia.market.core.service.LoginService;
import cn.lmjia.market.core.service.SystemService;
import com.google.zxing.WriterException;
import me.jiangcai.crud.row.FieldDefinition;
import me.jiangcai.crud.row.RowCustom;
import me.jiangcai.crud.row.RowDefinition;
import me.jiangcai.crud.row.field.FieldBuilder;
import me.jiangcai.crud.row.supplier.JQueryDataTableDramatizer;
import me.jiangcai.wx.standard.entity.StandardWeixinUser;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.persistence.criteria.Predicate;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 管理员工的控制器，除了root之外拥有grant权限的人 也可以管理，但是无法管理.
 *
 * @author CJ
 */
@Controller
@PreAuthorize("hasAnyRole('ROOT','" + Login.ROLE_MANAGER + "')")
public class ManageManagerController {

    @Autowired
    private LoginService loginService;
    @Autowired
    private LoginRepository loginRepository;
    @Autowired
    private QRController qrController;
    @Autowired
    private SystemService systemService;

    @GetMapping("/manage/bindManager{id}")
    public BufferedImage toScanImage(@PathVariable("id") long id) throws IOException, WriterException {
        return qrController.toQRCode(systemService.toUrl("/wechat/bindTo" + id));
    }

    /**
     * @param login 身份
     * @return 可管理的角色
     */
    private Set<ManageLevel> manageable(Login login) {
        Manager manager = (Manager) login;
        if (manager.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ROOT"))) {
            return Stream.of(ManageLevel.values()).collect(Collectors.toSet());
        }
        if (!manager.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_" + Login.ROLE_GRANT))) {
            return Collections.emptySet();
        }
        // 如果可以grant 则 只可以grant 它所拥有的所有角色（但不包括grant）
        return Stream.of(ManageLevel.values())
                .filter(level -> {
                    if (level == ManageLevel.root)
                        return false;
                    if (Arrays.asList(level.roles()).contains(Login.ROLE_GRANT))
                        return false;
                    // 只可以grant 它所拥有的所有角色（但不包括grant）
                    return manager.getAuthorities().containsAll(level.authorities());
                })
                .collect(Collectors.toSet());
    }

    @GetMapping("/manageManager")
    public String index() {
        return "_userManage.html";
    }

    @GetMapping("/manageManagerAdd")
    public String addIndex(@AuthenticationPrincipal Login login, Model model) {
        model.addAttribute("levels", manageable(loginService.get(login.getId())));
        return "_userEdit.html";
    }

    @GetMapping("/manageManagerEdit")
    public String editIndex(long id, @AuthenticationPrincipal Login login, Model model) {
        model.addAttribute("manager", loginService.get(id));
        model.addAttribute("levels", manageable(loginService.get(login.getId())));
        return "_userEdit.html";
    }

    @GetMapping("/manage/managers")
    @RowCustom(distinct = true, dramatizer = JQueryDataTableDramatizer.class)
    public RowDefinition list(String name, String department, String realName) {
        return new RowDefinition<Manager>() {
            @Override
            public Class<Manager> entityClass() {
                return Manager.class;
            }

            @Override
            public List<FieldDefinition<Manager>> fields() {
                return Arrays.asList(
                        FieldBuilder.asName(Manager.class, "id")
                                .addSelect(managerRoot -> managerRoot)
                                .addFormat(toBi(Login::getId))
                                .addOrder(managerRoot -> managerRoot.get("id"))
                                .build()
                        , FieldBuilder.asName(Manager.class, "name")
                                .addSelect(managerRoot -> null)
                                .addFormat(toBi(Login::getLoginName))
                                .addOrder(managerRoot -> managerRoot.get("loginName"))
                                .build()
                        , FieldBuilder.asName(Manager.class, "department")
                                .addSelect(managerRoot -> null)
                                .addFormat(toBi(Manager::getDepartment))
                                .addOrder(managerRoot -> managerRoot.get("department"))
                                .build()
                        , FieldBuilder.asName(Manager.class, "realName")
                                .addSelect(managerRoot -> null)
                                .addFormat(toBi(Manager::getRealName))
                                .addOrder(managerRoot -> managerRoot.get("realName"))
                                .build()
                        , FieldBuilder.asName(Manager.class, "wechatID")
                                .addSelect(managerRoot -> null)
                                .addFormat(toBi(manager -> {
                                    StandardWeixinUser user = manager.getWechatUser();
                                    if (user == null)
                                        return null;
                                    return user.getOpenId();
                                }))
                                .withoutOrder()
                                .build()
                        , FieldBuilder.asName(Manager.class, "role")
                                .addSelect(managerRoot -> null)
                                .addFormat(toBi(manager -> {
                                    Set<ManageLevel> levelSet = manager.getLevelSet();
                                    return levelSet.stream()
                                            .map(ManageLevel::title)
                                            .collect(Collectors.toList());
                                }))
                                .withoutOrder()
                                .build()
                        , FieldBuilder.asName(Manager.class, "remark")
                                .addSelect(managerRoot -> null)
                                .addFormat(toBi(Manager::getComment))
                                .addOrder(managerRoot -> managerRoot.get("comment"))
                                .build()
                        , FieldBuilder.asName(Manager.class, "state")
                                .addSelect(managerRoot -> null)
                                .addFormat(toBi(manager -> {
                                    boolean state = manager.isEnabled();
                                    return state ? "启用" : "禁用";
                                }))
                                .addOrder(managerRoot -> managerRoot.get("enabled"))
                                .build()
                        , FieldBuilder.asName(Manager.class, "stateCode")
                                .addSelect(managerRoot -> null)
                                .addFormat(toBi(manager -> {
                                    boolean state = manager.isEnabled();
                                    return state ? 0 : 1;
                                }))
                                .addOrder(managerRoot -> managerRoot.get("enabled"))
                                .build()
                );
            }

            @Override
            public Specification<Manager> specification() {
                // root 是不可见，也不可以编辑的
                return (root, query, cb) -> {
                    Predicate predicate = cb.notEqual(root.get("loginName"), "root");
                    if (!StringUtils.isEmpty(name)) {
                        predicate = cb.and(
                                predicate
                                , cb.like(root.get("loginName"), "%" + name + "%")
                        );
                    }
                    if (!StringUtils.isEmpty(department)) {
                        predicate = cb.and(
                                predicate
                                , cb.equal(root.get("department"), department)
                        );
                    }
                    if (!StringUtils.isEmpty(realName)) {
                        predicate = cb.and(
                                predicate
                                , cb.like(root.get("realName"), "%" + realName + "%")
                        );
                    }
                    return predicate;
                };
            }
        };
    }

    private BiFunction<Object, MediaType, Object> toBi(Function<Manager, Object> function) {
        return (o, mediaType) -> {
            if (o == null)
                return null;
            Manager manager = (Manager) o;
            return function.apply(manager);
        };
    }

    @PreAuthorize("hasAnyRole('ROOT','" + Login.ROLE_GRANT + "')")
    @PostMapping("/manage/manager")
    @Transactional
    public String updateUser(String name, String department, String realName, boolean enable, String comment
            , String[] role, @AuthenticationPrincipal Login login, long id) {
        updateManagerInfo(department, realName, enable, comment, role, loginService.get(login.getId())
                , () -> (Manager) loginService.get(id));
        return "redirect:/manageManager";
    }

    @PreAuthorize("hasAnyRole('ROOT','" + Login.ROLE_GRANT + "')")
    @PostMapping("/manage/managers")
    @Transactional
    public String addUser(String name, String department, String realName, boolean enable, String comment
            , String[] role, @AuthenticationPrincipal Login login, RedirectAttributes redirectAttributes) {

        Login current = loginService.get(login.getId());
        final String rawPassword = RandomStringUtils.randomAlphabetic(6);

        Supplier<Manager> managerSupplier = () -> {
            if (loginService.byLoginName(name) != null)
                throw new IllegalArgumentException(name + "已存在");
            return loginService.newLogin(Manager.class, name, current, rawPassword);
        };

        updateManagerInfo(department, realName, enable, comment, role, current, managerSupplier);

        redirectAttributes.addAttribute("rawPassword", rawPassword);
        return "redirect:/manageManager";
    }

    private void updateManagerInfo(String department, String realName, boolean enable, String comment, String[] role, Login current, Supplier<Manager> managerSupplier) {
        Set<ManageLevel> levelSet = Stream.of(role)
                .map(ManageLevel::valueOf)
                .collect(Collectors.toSet());
        if (levelSet.isEmpty())
            throw new IllegalArgumentException("角色不可为空。");
        if (!manageable(current).containsAll(levelSet)) {
            throw new IllegalArgumentException("越权操作。");
        }

        Manager manager = managerSupplier.get();

        manager.setLevelSet(levelSet);
        manager.setRealName(realName);
        manager.setComment(comment);
        manager.setDepartment(department);
        manager.setEnabled(enable);
    }

    @PutMapping("/login/{id}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void disable(@PathVariable("id") long id, @AuthenticationPrincipal Login current) {
        final Login login = loginService.get(id);
        manageLogin(current, login);
        login.setEnabled(false);
    }

    /**
     * 检查current是否可以管辖login
     *
     * @param currentInput
     * @param login
     */
    private void manageLogin(Login currentInput, Login login) {
        Login current = loginService.get(currentInput.getId());
        if (current.isRoot())
            // root 可以执行任何管理
            return;
        // 否者就看权限是否可以覆盖login了
        if (!(login instanceof Manager))
            return;
        if (!current.getAuthorities().containsAll(login.getAuthorities()))
            throw new IllegalArgumentException("越权操作。");
    }

    @PutMapping("/login/{id}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void enable(@PathVariable("id") long id, @AuthenticationPrincipal Login current) {
        final Login login = loginService.get(id);
        manageLogin(current, login);
        login.setEnabled(true);
    }

    @DeleteMapping("/login/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable("id") long id, @AuthenticationPrincipal Login current) {
        final Login login = loginService.get(id);
        manageLogin(current, login);
        loginRepository.delete(login);
    }

    @PutMapping("/login/{id}/password")
    @ResponseBody
    @Transactional
    public String password(@PathVariable("id") long id, @AuthenticationPrincipal Login current) {
        Login login = loginService.get(id);
        manageLogin(current, login);
        final String rawPassword = RandomStringUtils.randomAlphabetic(6);
        loginService.password(login, rawPassword);
        return rawPassword;
    }


}

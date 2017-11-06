package cn.lmjia.market.wechat.controller.help;

import cn.lmjia.market.core.service.SystemService;
import cn.lmjia.market.core.service.help.CommonProblemService;
import cn.lmjia.market.wechat.WechatTestBase;
import cn.lmjia.market.wechat.page.HelpCenterPage;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.*;

public class WechatCommonProblemControllerTest extends WechatTestBase{

    @Autowired
    private CommonProblemService commonProblemService;

    @Test
    public void index() throws Exception {

        String title = RandomStringUtils.randomAscii(10);
        commonProblemService.addAndEditCommonProblem(null, title,RandomStringUtils.randomAscii(20));

        //打开页面
        driver.get("http://localhost"+ SystemService.helpCenterURi);

        HelpCenterPage page = initPage(HelpCenterPage.class);

        page.assertHasTopic(title);


    }

}
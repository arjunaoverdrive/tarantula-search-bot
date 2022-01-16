package main.app.webapp.controllers;

import main.app.services.SiteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DefaultController {

    private final SiteService siteService;


    @Autowired
    public DefaultController(SiteService siteService) {
        this.siteService = siteService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

}

package org.igor.klimov.app.webapp.controllers;

import org.igor.klimov.app.services.SiteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@CrossOrigin(origins = "*")
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

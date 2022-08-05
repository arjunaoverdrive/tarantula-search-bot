package org.arjunaoverdrive.app.webapp.controllers;

import org.arjunaoverdrive.app.services.AllSitesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@CrossOrigin(origins = "*")
public class DefaultController {

    private final AllSitesService siteService;


    @Autowired
    public DefaultController(AllSitesService siteService) {
        this.siteService = siteService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

}

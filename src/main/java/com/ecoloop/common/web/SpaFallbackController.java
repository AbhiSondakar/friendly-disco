package com.ecoloop.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaFallbackController {
  @RequestMapping(value = {
    "/",
    "/{path:^(?!api|actuator|assets|swagger|v3)[^\\.]*$}"
  })
  public String forward() { return "forward:/index.html"; }
}

package com.ecoloop.classification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/ai")
public class AiController {
  @Value("${ecoloop.ai.provider:stub}") private String provider;
  @GetMapping("/health") public Map<String,Object> health(){ return Map.of("provider",provider,"providerConfigured",!provider.equalsIgnoreCase("stub")); }
}

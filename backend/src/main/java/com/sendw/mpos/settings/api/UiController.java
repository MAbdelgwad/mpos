package com.sendw.mpos.settings.api;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import com.sendw.mpos.settings.application.UiService;
@RestController
@RequestMapping("/api")
public class UiController {
    private final UiService service;
    public UiController(UiService service) { this.service=service; }
@GetMapping("/front-setting") public Map<String,Object> front() { return service.front(); }
@GetMapping("/ui-settings") public Map<String,Object> getSettings() { return service.getSettings(); }
@PutMapping("/ui-settings")
    public Map<String,Object> updateSettings(@RequestBody Map<String,String> body) { return service.updateSettings(body); }
@GetMapping("/config") public Map<String,Object> config() { return service.config(); }
@PostMapping("/change-language") public Map<String,Object> language(@RequestBody Map<String,String> b) { return service.language(b); }
@GetMapping("/catalog-summary") public Map<String,Object> summary() { return service.summary(); }
}

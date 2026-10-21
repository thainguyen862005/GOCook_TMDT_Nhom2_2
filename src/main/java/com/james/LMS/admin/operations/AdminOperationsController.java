package com.james.LMS.admin.operations;

import com.james.LMS.config.SecurityUserDetails;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ops")
@PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
public class AdminOperationsController {
    private final AdminOperationsService service;

    public AdminOperationsController(AdminOperationsService service) {this.service=service;}

    @GetMapping("/{resource}")
    public Map<String,Object> list(@PathVariable String resource,
            @RequestParam(defaultValue="") String q, @RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size) {
        return service.page(resource,q,status,page,size);
    }

    @GetMapping("/{resource}/{id}")
    public Map<String,Object> get(@PathVariable String resource,@PathVariable Long id) {
        return service.one(resource,id);
    }

    @GetMapping("/profiles/{kind}/{id}")
    public Map<String,Object> profile(@PathVariable String kind,@PathVariable Long id) {
        if(!kind.equals("chefs") && !kind.equals("customers"))
            throw new IllegalArgumentException("Chỉ hỗ trợ hồ sơ khách hàng/đầu bếp.");
        return service.overview(id,kind.equals("chefs"));
    }

    @GetMapping("/orders/{id}/timeline")
    public Map<String,Object> orderTimeline(@PathVariable Long id) {return service.orderTimeline(id);}

    @GetMapping("/summary")
    public Object summary() {return service.summary();}

    @PostMapping("/{resource}")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@PathVariable String resource,@RequestBody Map<String,Object> body,
         @AuthenticationPrincipal SecurityUserDetails user) {
        return service.create(resource,body,user.getId());
    }

    @PatchMapping("/{resource}/{id}")
    public Map<String,Object> update(@PathVariable String resource,@PathVariable Long id,
          @RequestBody Map<String,Object> body,@AuthenticationPrincipal SecurityUserDetails user) {
        return service.change(resource,id,body,user.getId());
    }

    @PatchMapping("/chefs/{id}/verify")
    public Map<String,Object> verify(@PathVariable Long id,@RequestBody Map<String,Object> body,
          @AuthenticationPrincipal SecurityUserDetails user) {
        return service.verifyChef(id,String.valueOf(body.get("status")),String.valueOf(body.get("reason")),user.getId());
    }

    @PatchMapping("/chefs/{id}/active")
    public Map<String,Object> active(@PathVariable Long id,@RequestBody Map<String,Object> body,
          @AuthenticationPrincipal SecurityUserDetails user) {
        if(!(body.get("active") instanceof Boolean active))
            throw new IllegalArgumentException("active phải là boolean.");
        return service.suspendChef(id,active,String.valueOf(body.get("reason")),user.getId());
    }
}

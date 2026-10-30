package com.james.LMS.publiccontent;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WardDirectoryController {
  private final WardDirectory wards;
  public WardDirectoryController(WardDirectory wards) { this.wards = wards; }

  @GetMapping("/api/public/wards")
  @PreAuthorize("isAuthenticated()")
  public List<WardDirectory.Ward> list(@RequestParam String province) {
    return wards.wards(province);
  }
}

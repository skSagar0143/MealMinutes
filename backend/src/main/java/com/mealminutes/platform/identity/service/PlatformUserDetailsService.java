package com.mealminutes.platform.identity.service;

import com.mealminutes.platform.identity.domain.AccountStatus;
import com.mealminutes.platform.identity.persistence.PlatformUserAccountRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.Locale;

@Service
public class PlatformUserDetailsService implements UserDetailsService {
    private final PlatformUserAccountRepository users;
    public PlatformUserDetailsService(PlatformUserAccountRepository users) { this.users = users; }
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var account = users.findByEmail(email == null ? "" : email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return User.withUsername(account.getEmail()).password(account.getPasswordHash())
                .authorities("ROLE_" + account.getPlatformRole().name())
                .accountLocked(account.getAccountStatus() == AccountStatus.LOCKED)
                .disabled(account.getAccountStatus() == AccountStatus.DISABLED).build();
    }
}
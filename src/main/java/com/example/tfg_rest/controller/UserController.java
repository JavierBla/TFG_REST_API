package com.example.tfg_rest.controller;

import com.example.tfg_rest.controller.utils.ControllerValidationErrors;
import com.example.tfg_rest.mappers.UserRegisterDtoMapper;
import com.example.tfg_rest.models.dao.CommentDAOImpl;
import com.example.tfg_rest.models.dao.RoleDAOImpl;
import com.example.tfg_rest.models.dao.TFGRegisterDAOImpl;
import com.example.tfg_rest.models.dao.UserDAOImpl;
import com.example.tfg_rest.models.dto.UpdatePassword;
import com.example.tfg_rest.models.dto.UpdateUserEntity;
import com.example.tfg_rest.models.dto.UserRegisterDTO;
import com.example.tfg_rest.models.entity.Comment;
import com.example.tfg_rest.models.entity.Role;
import com.example.tfg_rest.models.entity.TFGRegister;
import com.example.tfg_rest.models.entity.User;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserDAOImpl userService;

    @Autowired
    private TFGRegisterDAOImpl tfgRegisterService;

    @Autowired
    private RoleDAOImpl roleService;

    @Autowired
    private CommentDAOImpl commentService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("")
    public ResponseEntity<List<User>> showAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    @GetMapping("/{username}")
    @PreAuthorize("hasAuthority('ADMIN') or #username == principal  or #username == 'me'")
    public ResponseEntity<?> show(@PathVariable String username) {

        User user = username.equals("me") ? getUserIfMe() : userService.findByUsername(username);
        return user != null ? ResponseEntity.ok(user) : ResponseEntity.notFound().build();
    }

    private User getUserIfMe() {
        String requestUsername = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        return userService.findByUsername(requestUsername);
    }

    @PostMapping("")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> create(@RequestBody @Valid UserRegisterDTO user, BindingResult result) {

        if (userService.findByUsername(user.username) != null) {
            return ResponseEntity.badRequest().body("Username already exists");
        }

        if (userService.findByEmail(user.email) != null) {
            return ResponseEntity.badRequest().body("Email already exists");
        }

        if (result.hasFieldErrors()) {
            List<String> errors = result.getFieldErrors()
                    .stream()
                    .map(err -> "The field '\\{err.getField()}' \\{err.getDefaultMessage()}").toList();

            return ResponseEntity.badRequest().body(errors);
        }

        Role role = roleService.findByRoleName("USER");

        User userEntity = UserRegisterDtoMapper.map(user);

        userEntity.setRolesAssociated(Set.of(role));
        userEntity.setPassword(passwordEncoder.encode(user.password));

        userService.save(userEntity);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{username}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable String username) {
        User user = userService.findByUsername(username);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        userService.delete(user.getId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{username}")
    @PreAuthorize("hasAuthority('ADMIN') or #username == principal or #username == 'me'")
    public ResponseEntity<?> update(@PathVariable String username, @RequestBody @Valid UpdateUserEntity newUser, BindingResult result) {

        User userFound = userService.findByUsername(username.equals("me") ? SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString() : username);


        if (userFound == null) {
            return ResponseEntity.notFound().build();
        }
        if (result.hasFieldErrors()) {
            return ControllerValidationErrors.generateFieldErrors(result);
        }
        if (!newUser.username.equals(userFound.getUsername()) && userService.findByUsername(newUser.username) != null) {
            return ResponseEntity.badRequest().body("Username already exists");
        }

        userFound.setUsername(newUser.username);
        userFound.setName(newUser.name);
        userFound.setSurnames(newUser.surnames);
        userFound.setEmail(newUser.email);

        userService.update(userFound);
        return ResponseEntity.ok(newUser);
    }

    @PutMapping("/{username}/password")
    @PreAuthorize("hasAuthority('ADMIN') or #username == principal or #username == 'me'")
    public ResponseEntity<?> updatePassword(@PathVariable String username, @RequestBody UpdatePassword password) {

        String actualUsername = username.equals("me") ? SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString() : username;

        User user = userService.findByUsername(actualUsername);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        user.setPassword(passwordEncoder.encode(password.password));
        userService.update(user);

        return ResponseEntity.ok().build();
    }

    // ----------------- TFG Registers -----------------

    @GetMapping("/{username}/registers")
    @PreAuthorize("hasAuthority('ADMIN') or #username == principal or #username == 'me'")
    public ResponseEntity<?> showRegisters(@PathVariable String username) {
        User user = username.equals("me") ? getUserIfMe() : userService.findByUsername(username);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(user.getRegisters());
    }

    @GetMapping("/{username}/registers/{registerId}")
    @PreAuthorize("hasAuthority('ADMIN') or #username == principal or #username == 'me'")
    public ResponseEntity<?> showRegister(@PathVariable String username, @PathVariable long registerId) {
        User user = username.equals("me") ? getUserIfMe() : userService.findByUsername(username);
        if (user == null) {
            ResponseEntity.notFound().build();
        }

        assert user != null;
        return ResponseEntity.ok(user.getRegisters().stream().filter(tfgRegister -> tfgRegister.getId() == registerId).findFirst().orElse(null));
    }

    @PostMapping("/{username}/registers")
    @PreAuthorize("hasAuthority('ADMIN') or #username == principal or #username == 'me'")
    public ResponseEntity<?> createRegister(@PathVariable String username, @RequestBody TFGRegister register) {

        User user = username.equals("me") ? getUserIfMe() : userService.findByUsername(username);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        register.setUser(user);
        register.setRegister_date(new Date());

        tfgRegisterService.save(register);

        return ResponseEntity.ok(user.getRegisters());
    }

    // ----------------- Comments -----------------

    @GetMapping("/{username}/comments")
    @PreAuthorize("hasAuthority('ADMIN') or #username == principal or #username == 'me'")
    public ResponseEntity<?> showComments(@PathVariable String username) {
        User user = username.equals("me") ? getUserIfMe() : userService.findByUsername(username);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(user.getRegisters());
    }

    @GetMapping("/{username}/comments/{commentId}")
    @PreAuthorize("hasAuthority('ADMIN') or #username == principal or #username == 'me'")
    public ResponseEntity<?> showComment(@PathVariable String username, @PathVariable long commentId) {
        User user = username.equals("me") ? getUserIfMe() : userService.findByUsername(username);
        if (user == null) {
            ResponseEntity.notFound().build();
        }

        assert user != null;
        return ResponseEntity.ok(user.getRegisters().stream().filter(tfgRegister -> tfgRegister.getId() == commentId).findFirst().orElse(null));
    }

    @PostMapping("/{username}/comments")
    @PreAuthorize("hasAuthority('ADMIN') or #username == principal or #username == 'me'")
    public ResponseEntity<?> createComment(@PathVariable String username, @RequestBody Comment comment) {

        User user = username.equals("me") ? getUserIfMe() : userService.findByUsername(username);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        comment.setUser(user);
        comment.setDate(new Date());

        commentService.save(comment);

        return ResponseEntity.ok(user.getComments());
    }
}
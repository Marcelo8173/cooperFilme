package com.eureka.cooperfilme.controllers;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.eureka.cooperfilme.domain.scripts.Scripts;
import com.eureka.cooperfilme.domain.scripts.enuns.ScriptsStatus;
import com.eureka.cooperfilme.domain.user.enuns.UserRoles;
import com.eureka.cooperfilme.services.useCases.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.eureka.cooperfilme.domain.scripts.scriptsDTO.CreateSriptDTO;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.net.URLDecoder;
import java.util.UUID;

import com.eureka.cooperfilme.domain.scripts.scriptsDTO.ScriptTitleAndStatusDTO;

@RestController
@RequestMapping(value = "/scripts")
public class ScriptsController {
    @Autowired
    CreateScriptService scriptService;

    @Autowired
    ListScriptsBySearchService listScriptsBySearchService;

    @Autowired
    ScriptCheckDetails scriptCheckDetails;

    @Autowired
    ScriptsListAll scriptsListAll;

    @Autowired
    TokenService tokenService;
    @Autowired
    ScriptUpdateService scriptUpdateService;
    @Value("${api.security.token.secret}")
    private String secret;

    @PostMapping
    public ResponseEntity<CreateSriptDTO> createScript(@RequestBody @Valid CreateSriptDTO createSriptDTO) {
        Scripts scrpitSaved = scriptService.createScript(createSriptDTO);

        URI uri = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(scrpitSaved.getId()).toUri();
        return ResponseEntity.created(uri).body(new CreateSriptDTO(scrpitSaved.getTitle(),
                scrpitSaved.getContent(),
                scrpitSaved.getClient().getName(),
                scrpitSaved.getClient().getEmail(),
                scrpitSaved.getClient().getPhone()));
    }

    @GetMapping("/check")
    public ResponseEntity<List<ScriptTitleAndStatusDTO>> listScripts(@RequestParam(value = "search", defaultValue = "") String search) {
        search = URLDecoder.decode(search);
        if (search.trim().isEmpty()) {
            return ResponseEntity.ok().body(Collections.emptyList());
        }
        List<Scripts> scripts = listScriptsBySearchService.listScripts(search, search);

        List<ScriptTitleAndStatusDTO> scriptStatusTitles = scripts.stream()
                .map(script -> new ScriptTitleAndStatusDTO(script.getStatus().name(), script.getTitle()))
                .toList();
        return ResponseEntity.ok().body(scriptStatusTitles);
    }

    @GetMapping("/check/details/{id}")
    public ResponseEntity<Scripts> checkDetailsOnScript(@PathVariable UUID id) {
        var response = scriptCheckDetails.listDetailsOnScript(id)
                .map(script -> ResponseEntity.ok(script))
                .orElseGet(() -> ResponseEntity.notFound().build());
        return response;
    }

    @GetMapping
    public ResponseEntity<List<Scripts>> listAll(@RequestParam(value = "status", required = false) ScriptsStatus status,
                                                 @RequestParam(value = "sendDate", required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sendDate,
                                                 @RequestParam(value = "email", required = false) String email) {
        return ResponseEntity.ok().body(scriptsListAll.listAllScripts(status, sendDate, email));
    }

    @PostMapping("/change/{id}")
    public ResponseEntity<Void> changeStatus(@PathVariable UUID id, @RequestBody String nextStatus, @RequestHeader("Authorization") String authHeader) {
        UserRoles userRole = getUserRoleFromToken(authHeader);
        ScriptsStatus currentStatus = getCurrentStatus(id);
//
            if (!canChangeStatus(userRole, currentStatus, nextStatus)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
//            scriptUpdateService.updateScriptStatus(id, nextStatus);
        return ResponseEntity.ok().build();


    }

    public UserRoles getUserRoleFromToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                Algorithm algorithm = Algorithm.HMAC256(secret);
                var verifier = JWT.require(algorithm).build();

                var decodedJWT = verifier.verify(token);

                String role = decodedJWT.getClaim("role").asString();

                return UserRoles.valueOf(role);
            } catch (Exception e) {
                throw new RuntimeException("It's not prossible to process token", e);
            }
        } else {
            throw new IllegalArgumentException("Header invalid");
        }
    }

    private ScriptsStatus getCurrentStatus(UUID id) {
        return scriptCheckDetails.getStatusById(id);
    }

    private boolean canChangeStatus(UserRoles role, ScriptsStatus currentStatus, String nextStatus) {
        switch (role) {
            case ANALISTA:
                return (currentStatus.equals("aguardando_analise") && nextStatus.equals("em_analise")) ||
                        (currentStatus.equals("em_analise") && (nextStatus.equals("aguardando_revisao") || nextStatus.equals("recusado")));
            case REVISOR:
                return (currentStatus.equals("aguardando_revisao") && nextStatus.equals("em_revisao")) ||
                        (currentStatus.equals("em_revisao") && nextStatus.equals("aguardando_aprovacao"));
            case APROVADORES:
                return (currentStatus.equals("aguardando_aprovacao") && nextStatus.equals("em_aprovacao")) ||
                        (currentStatus.equals("em_aprovacao") && (nextStatus.equals("aprovado") || nextStatus.equals("recusado")));
            default:
                return false;
        }
    }
}

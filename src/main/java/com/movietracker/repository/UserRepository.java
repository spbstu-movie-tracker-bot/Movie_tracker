package com.movietracker.repository;

import com.movietracker.model.AppUser;
import com.movietracker.model.Role;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDate;

import java.time.LocalDateTime;

import static com.movietracker.db.JooqTables.*;

// User data access with jOOQ
@Repository
public class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);
    private final DSLContext dsl;

    public UserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Mono<AppUser> findByTelegramId(Long telegramId) {
        return Mono.from(
                dsl.selectFrom(USERS)
                        .where(USERS_TELEGRAM_ID.eq(telegramId))
        ).map(this::mapToUser);
    }

    public Mono<AppUser> findById(Long id) {
        return Mono.from(
                dsl.selectFrom(USERS)
                        .where(USERS_ID.eq(id))
        ).map(this::mapToUser);
    }

    public Flux<AppUser> findAll() {
        return Flux.from(
                dsl.selectFrom(USERS)
                        .orderBy(USERS_ID)
        ).map(this::mapToUser);
    }

    public Mono<AppUser> save(AppUser user) {
        return Mono.from(
                dsl.insertInto(USERS)
                        .set(USERS_TELEGRAM_ID, user.getTelegramId())
                        .set(USERS_USERNAME, user.getUsername())
                        .set(USERS_FIRST_NAME, user.getFirstName())
                        .set(USERS_LAST_NAME, user.getLastName())
                        .set(USERS_ROLE, user.getRole().name())
                        .set(USERS_REGISTERED_AT, user.getRegisteredAt())
                        .set(USERS_IS_ACTIVE, user.getIsActive())
                        .returning()
        ).map(record -> {
            log.info("Saved new user with id={}", record.get(USERS_ID));
            return mapToUser(record);
        });
    }

    public Mono<Integer> updateRole(Long userId, Role role) {
        return Mono.from(
                dsl.update(USERS)
                        .set(USERS_ROLE, role.name())
                        .where(USERS_ID.eq(userId))
        );
    }

    public Mono<Integer> updateBirthDate(Long userId, LocalDate birthDate) {
        return Mono.from(
                dsl.update(USERS)
                        .set(USERS_BIRTH_DATE, birthDate)
                        .where(USERS_ID.eq(userId))
        );
    }

    private AppUser mapToUser(org.jooq.Record record) {
        Object rawTimestamp = record.get(USERS_REGISTERED_AT);
        LocalDateTime registeredAt;
        if (rawTimestamp instanceof LocalDateTime ldt) {
            registeredAt = ldt;
        } else if (rawTimestamp instanceof java.sql.Timestamp ts) {
            registeredAt = ts.toLocalDateTime();
        } else {
            registeredAt = LocalDateTime.now();
        }

        Object rawBirthDate = record.get(USERS_BIRTH_DATE);
        LocalDate birthDate = null;
        if (rawBirthDate instanceof LocalDate ld) {
            birthDate = ld;
        } else if (rawBirthDate instanceof java.sql.Date sd) {
            birthDate = sd.toLocalDate();
        }


        return new AppUser(
                record.get(USERS_ID),
                record.get(USERS_TELEGRAM_ID),
                record.get(USERS_USERNAME),
                record.get(USERS_FIRST_NAME),
                record.get(USERS_LAST_NAME),
                Role.valueOf(record.get(USERS_ROLE)),
                registeredAt,
                birthDate,
                record.get(USERS_IS_ACTIVE)
        );
    }
}

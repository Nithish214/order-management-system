package com.learn.orderservice.repository;

import com.learn.orderservice.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByCognitoSub(String cognitoSub);

    // Inserts the placeholder row only if this Cognito identity has none yet, and does nothing
    // -- rather than throwing -- if another request got there first. That matters because a
    // brand-new user's very first login fires several requests at once (the profile sync, the
    // cart load), each of which used to do "look up, else insert": both could miss the lookup,
    // and the loser hit the unique constraint on cognito_sub and returned a 500. A failed
    // INSERT also aborts the whole Postgres transaction, so catching that exception and
    // retrying the lookup in the same transaction is not an option; ON CONFLICT DO NOTHING
    // never raises, so the transaction stays healthy. If the other request is still
    // uncommitted, this statement waits for it and then skips; the lookup afterwards sees it.
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO app_user (id, cognito_sub, email, name)
            VALUES (nextval('app_user_seq'), :sub, :email, :name)
            ON CONFLICT (cognito_sub) DO NOTHING
            """)
    int insertIfAbsent(@Param("sub") String cognitoSub, @Param("email") String email, @Param("name") String name);

    // Must be called inside a (non-read-only) transaction, like the insert it wraps. The
    // placeholder email/name are the same ones every earlier copy of this used; the next
    // login's POST /users/me sync replaces them with the real ones.
    default AppUser findOrCreate(String cognitoSub) {
        insertIfAbsent(cognitoSub, cognitoSub + "@cognito.local", "Cognito User");
        return findByCognitoSub(cognitoSub).orElseThrow();
    }
}

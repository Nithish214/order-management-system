package com.learn.orderservice.service;

import com.learn.orderservice.dto.AddressRequest;
import com.learn.orderservice.dto.AddressResponse;
import com.learn.orderservice.entity.AppUser;
import com.learn.orderservice.entity.ShippingAddress;
import com.learn.orderservice.exception.ConflictException;
import com.learn.orderservice.repository.AppUserRepository;
import com.learn.orderservice.repository.ShippingAddressRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// The address book's write logic, each operation one @Transactional unit -- same transactional-
// service style as OrderCreationService.
//
// THE DEFAULT-ADDRESS RULE ("at most one default per user") is enforced twice, deliberately:
//   1. Here, in application logic: promoting an address first clears the old default, then sets
//      the new one, in the same transaction -- so the normal, single-request case just works
//      and the user never sees an error.
//   2. In the database, by V25's partial unique index (one is_default row per user_id) -- the
//      backstop. Logic alone is check-then-act, and two requests promoting different addresses
//      at the same instant can both get past it. The index makes that impossible; the slower
//      request gets a constraint violation, translated below into a 409 the user can simply
//      retry. Either one alone is worse: logic-only can corrupt under concurrency, index-only
//      would throw on every ordinary "make this my default" because the old default is still set.
//
// What's a "default" for: it's what checkout preselects. So a user with any saved address always
// has exactly one -- the first address saved becomes it automatically, and deleting the default
// promotes the newest remaining one (see delete) -- never an address book with entries but no
// default, which would leave checkout with nothing preselected.
@Service
public class AddressService {

    // Not a scaling limit, an abuse limit: a free-text field users can add rows to needs a cap,
    // or one account could fill the table. Generous for a real person.
    static final int MAX_ADDRESSES_PER_USER = 10;

    private final ShippingAddressRepository shippingAddressRepository;
    private final AppUserRepository appUserRepository;
    private final UserAccountService userAccountService;

    public AddressService(
            ShippingAddressRepository shippingAddressRepository,
            AppUserRepository appUserRepository,
            UserAccountService userAccountService
    ) {
        this.shippingAddressRepository = shippingAddressRepository;
        this.appUserRepository = appUserRepository;
        this.userAccountService = userAccountService;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(String cognitoSub) {
        // No app_user row yet just means no saved addresses -- not an error.
        return appUserRepository.findByCognitoSub(cognitoSub)
                .map(user -> shippingAddressRepository
                        .findByUserIdOrderByDefaultAddressDescCreatedAtDescIdDesc(user.getId())
                        .stream()
                        .map(AddressResponse::from)
                        .toList())
                .orElse(List.of());
    }

    @Transactional
    public AddressResponse create(String cognitoSub, AddressRequest request) {
        AppUser user = userAccountService.findOrCreate(cognitoSub);
        long existing = shippingAddressRepository.countByUserId(user.getId());
        if (existing >= MAX_ADDRESSES_PER_USER) {
            throw new ConflictException(
                    "You can save up to " + MAX_ADDRESSES_PER_USER + " addresses. Delete one to add another.");
        }

        // The first address is always the default; after that, only if asked to be.
        boolean makeDefault = existing == 0 || Boolean.TRUE.equals(request.isDefault());
        if (makeDefault) {
            shippingAddressRepository.clearDefault(user.getId());
        }

        ShippingAddress address = new ShippingAddress();
        // A reference, not a load: clearDefault above clears the persistence context, and all
        // this row needs from the user is its id for the foreign key.
        address.setUser(appUserRepository.getReferenceById(user.getId()));
        apply(address, request);
        address.setDefaultAddress(makeDefault);
        return AddressResponse.from(saveOrConflict(address));
    }

    @Transactional
    public AddressResponse update(String cognitoSub, Long addressId, AddressRequest request) {
        AppUser user = requireUser(cognitoSub);
        ShippingAddress address = findOwned(user, addressId);

        // isDefault=true on a non-default address promotes it. false/absent changes nothing: the
        // only way to stop an address being the default is to make ANOTHER one the default (or
        // delete it) -- letting a PUT demote it would strand the user with no default.
        boolean promote = Boolean.TRUE.equals(request.isDefault()) && !address.isDefaultAddress();
        if (promote) {
            shippingAddressRepository.clearDefault(user.getId());
            // clearDefault clears the persistence context, so the instance loaded above is no
            // longer managed -- reload for a managed copy to apply the changes to.
            address = findOwned(user, addressId);
        }

        apply(address, request);
        if (promote) {
            address.setDefaultAddress(true);
        }
        return AddressResponse.from(saveOrConflict(address));
    }

    @Transactional
    public void delete(String cognitoSub, Long addressId) {
        AppUser user = requireUser(cognitoSub);
        ShippingAddress address = findOwned(user, addressId);
        boolean wasDefault = address.isDefaultAddress();

        shippingAddressRepository.delete(address);
        // Flushed now so the deleted default row is really gone before another row is promoted
        // in its place -- otherwise the unique index would briefly see two defaults.
        shippingAddressRepository.flush();

        // Past orders are unaffected either way: they hold a snapshot copy, not a reference to
        // this row (see ShippingAddressSnapshot) -- that's what makes deleting always safe.
        if (wasDefault) {
            shippingAddressRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(user.getId())
                    .ifPresent(next -> next.setDefaultAddress(true));
        }
    }

    private AppUser requireUser(String cognitoSub) {
        // Same message as findOwned's: a user with no row has no addresses, and the caller
        // shouldn't be able to tell "no account" from "not your address" from "no such address".
        return appUserRepository.findByCognitoSub(cognitoSub)
                .orElseThrow(() -> new EntityNotFoundException("Address not found"));
    }

    private ShippingAddress findOwned(AppUser user, Long addressId) {
        return shippingAddressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Address not found"));
    }

    // Trims every field and turns a blank line2 into null -- so "  " never gets stored as an
    // address line, and two addresses that differ only in stray whitespace are the same.
    private void apply(ShippingAddress address, AddressRequest request) {
        address.setLabel(request.label().strip());
        address.setLine1(request.line1().strip());
        address.setLine2(request.line2() == null || request.line2().isBlank() ? null : request.line2().strip());
        address.setCity(request.city().strip());
        address.setState(request.state().strip());
        address.setPostalCode(request.postalCode().strip());
        address.setCountry(request.country().strip());
    }

    // saveAndFlush (not save) so a violation of the one-default index surfaces HERE, where it
    // can become a clear 409, instead of at commit time as an opaque 500.
    private ShippingAddress saveOrConflict(ShippingAddress address) {
        try {
            return shippingAddressRepository.saveAndFlush(address);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(
                    "Your default address was just changed by another request. Please try again.");
        }
    }
}

package io.github.hectorvent.floci.services.ssoportal;

import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.PaginatedResult;
import io.github.hectorvent.floci.core.common.Pagination;
import io.github.hectorvent.floci.services.organizations.OrganizationsService;
import io.github.hectorvent.floci.services.ssoadmin.SsoAdminService;
import io.github.hectorvent.floci.services.ssooidc.SsoOidcException;
import io.github.hectorvent.floci.services.ssooidc.SsoOidcService;
import io.github.hectorvent.floci.services.ssooidc.model.TokenSession;
import io.github.hectorvent.floci.services.ssoportal.model.PortalAccountInfo;
import io.github.hectorvent.floci.services.ssoportal.model.PortalRoleInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class SsoPortalService {
    private final SsoOidcService oidcService;
    private final SsoAdminService ssoAdminService;
    private final OrganizationsService organizationsService;

    @Inject
    public SsoPortalService(SsoOidcService oidcService,
                            SsoAdminService ssoAdminService,
                            OrganizationsService organizationsService) {
        this.oidcService = oidcService;
        this.ssoAdminService = ssoAdminService;
        this.organizationsService = organizationsService;
    }

    public PaginatedResult<PortalAccountInfo> listAccounts(
            String accessToken, String maxResults, String nextToken) {
        TokenSession session = requirePortalSession(accessToken);
        Map<String, PortalAccountInfo> accounts = new LinkedHashMap<>();
        ssoAdminService.portalAssignmentsForUser(session.principalId()).forEach(assignment -> {
            if (accounts.containsKey(assignment.accountId())) {
                return;
            }
            PortalAccountInfo info = organizationsService.findAccountForPortal(assignment.accountId())
                    .map(account -> new PortalAccountInfo(account.getId(), account.getName(), account.getEmail()))
                    .orElseGet(() -> new PortalAccountInfo(assignment.accountId(), null, null));
            accounts.put(assignment.accountId(), info);
        });
        Integer pageSize = Pagination.parseMaxResults(maxResults, "InvalidRequestException");
        List<PortalAccountInfo> values = accounts.values().stream().toList();
        return Pagination.paginate(values, PortalAccountInfo::accountId,
                pageSize, nextToken, 100, 100, "InvalidRequestException");
    }

    public PaginatedResult<PortalRoleInfo> listAccountRoles(
            String accessToken, String accountId, String maxResults, String nextToken) {
        TokenSession session = requirePortalSession(accessToken);
        if (accountId == null || !accountId.matches("\\d{12}")) {
            throw new AwsException("InvalidRequestException", "accountId must be a 12-digit AWS account identifier.", 400);
        }
        Map<String, PortalRoleInfo> roles = new LinkedHashMap<>();
        ssoAdminService.portalAssignmentsForUser(session.principalId()).stream()
                .filter(assignment -> accountId.equals(assignment.accountId()))
                .forEach(assignment -> {
                    var permissionSet = ssoAdminService.permissionSetForPortal(assignment.permissionSetArn());
                    roles.putIfAbsent(assignment.permissionSetArn(),
                            new PortalRoleInfo(accountId, permissionSet.name()));
                });
        Integer pageSize = Pagination.parseMaxResults(maxResults, "InvalidRequestException");
        List<PortalRoleInfo> values = roles.values().stream().toList();
        return Pagination.paginate(values, PortalRoleInfo::roleName,
                pageSize, nextToken, 100, 100, "InvalidRequestException");
    }

    public TokenSession requirePortalSession(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw unauthorized("The access token is missing or invalid.");
        }
        try {
            TokenSession session = oidcService.requireAccessToken(accessToken);
            if (session.principalId() == null || session.principalId().isBlank()) {
                throw unauthorized("The access token is not associated with an authenticated user.");
            }
            return session;
        } catch (SsoOidcException e) {
            throw unauthorized("The access token is missing, invalid, or expired.");
        }
    }

    private static AwsException unauthorized(String message) {
        return new AwsException("UnauthorizedException", message, 401);
    }
}

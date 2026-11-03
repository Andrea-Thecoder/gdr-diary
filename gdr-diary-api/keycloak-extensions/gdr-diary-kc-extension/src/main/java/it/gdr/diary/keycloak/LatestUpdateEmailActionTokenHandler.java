package it.gdr.diary.keycloak;

import java.util.Arrays;

import org.keycloak.TokenVerifier;
import org.keycloak.authentication.actiontoken.ActionTokenContext;
import org.keycloak.authentication.actiontoken.updateemail.UpdateEmailActionToken;
import org.keycloak.authentication.actiontoken.updateemail.UpdateEmailActionTokenHandler;
import org.keycloak.models.UserModel;

public class LatestUpdateEmailActionTokenHandler extends UpdateEmailActionTokenHandler {

    private static final int CLOCK_SLACK_SECONDS = 5;

    @Override
    public TokenVerifier.Predicate<? super UpdateEmailActionToken>[] getVerifiers(
            ActionTokenContext<UpdateEmailActionToken> tokenContext) {
        TokenVerifier.Predicate<? super UpdateEmailActionToken>[] inherited = super.getVerifiers(tokenContext);
        TokenVerifier.Predicate<? super UpdateEmailActionToken>[] all = Arrays.copyOf(inherited, inherited.length + 1);
        TokenVerifier.Predicate<UpdateEmailActionToken> latest = token -> isLatest(tokenContext, token);
        all[inherited.length] = latest;
        return all;
    }

    @Override
    public int order() {
        return super.order() + 1;
    }

    private boolean isLatest(ActionTokenContext<UpdateEmailActionToken> tokenContext, UpdateEmailActionToken token) {
        UserModel user = tokenContext.getSession().users().getUserById(tokenContext.getRealm(), token.getUserId());
        if (user == null) {
            return false;
        }
        String pending = user.getFirstAttribute(ResendEmailChangeResource.PENDING_ATTRIBUTE);
        if (pending == null || !pending.equalsIgnoreCase(token.getNewEmail())) {
            return false;
        }
        String resentAt = user.getFirstAttribute(ResendEmailChangeResource.RESENT_ATTRIBUTE);
        Long expires = token.getExp();
        if (resentAt == null || expires == null) {
            return true;
        }
        try {
            int lifespan = tokenContext.getRealm().getActionTokenGeneratedByUserLifespan(UpdateEmailActionToken.TOKEN_TYPE);
            return expires - lifespan >= Long.parseLong(resentAt) - CLOCK_SLACK_SECONDS;
        } catch (NumberFormatException cause) {
            return true;
        }
    }
}

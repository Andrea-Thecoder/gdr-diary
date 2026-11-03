package it.gdr.diary.keycloak;

import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.requiredactions.UpdateEmail;
import org.keycloak.models.UserModel;

public class NonBlockingUpdateEmail extends UpdateEmail {

    private static final String ACTION = "UPDATE_EMAIL";

    @Override
    public void processAction(RequiredActionContext context) {
        super.processAction(context);
        UserModel user = context.getUser();
        user.removeRequiredAction(ACTION);
        String pending = user.getFirstAttribute(ResendEmailChangeResource.PENDING_ATTRIBUTE);
        if (pending != null && !pending.isBlank()) {
            context.getSession().singleUseObjects().putIfAbsent(
                ResendEmailChangeResource.cooldownKey(user), ResendEmailChangeResource.COOLDOWN_SECONDS);
        }
    }

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
    }

    @Override
    public int order() {
        return super.order() + 1;
    }
}

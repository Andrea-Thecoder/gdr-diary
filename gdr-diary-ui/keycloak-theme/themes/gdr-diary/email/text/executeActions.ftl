<#ftl output_format="plainText">
<#assign requiredActionsText><#if requiredActions??><#list requiredActions><#items as reqActionItem>${msg("requiredAction.${reqActionItem}")}<#sep>, </#items></#list></#if></#assign>
${msg("gdrEmailActionsTitle")}

${msg("gdrEmailActionsText", requiredActionsText)}

${link}

${msg("gdrEmailExpires", linkExpirationFormatter(linkExpiration))}

${msg("gdrEmailActionsIgnore")}

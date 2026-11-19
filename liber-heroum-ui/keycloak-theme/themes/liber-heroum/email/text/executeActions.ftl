<#ftl output_format="plainText">
<#assign requiredActionsText><#if requiredActions??><#list requiredActions><#items as reqActionItem>${msg("requiredAction.${reqActionItem}")}<#sep>, </#items></#list></#if></#assign>
${msg("lhEmailActionsTitle")}

${msg("lhEmailActionsText", requiredActionsText)}

${link}

${msg("lhEmailExpires", linkExpirationFormatter(linkExpiration))}

${msg("lhEmailActionsIgnore")}

<#ftl output_format="HTML">
<#import "template.ftl" as layout>
<#assign requiredActionsText><#if requiredActions??><#list requiredActions><#items as reqActionItem>${msg("requiredAction.${reqActionItem}")}<#sep>, </#items></#list></#if></#assign>
<@layout.emailLayout eyebrow=msg("gdrEmailActionsEyebrow") title=msg("gdrEmailActionsTitle") preheader=msg("gdrEmailActionsText", requiredActionsText)>
<@layout.text>${msg("gdrEmailActionsText", requiredActionsText)}</@layout.text>
<@layout.action href=link label=msg("gdrEmailActionsButton") />
<@layout.note>${msg("gdrEmailExpires", linkExpirationFormatter(linkExpiration))}</@layout.note>
<@layout.note>${msg("gdrEmailActionsIgnore")}</@layout.note>
</@layout.emailLayout>

<#ftl output_format="HTML">
<#import "template.ftl" as layout>
<#assign requiredActionsText><#if requiredActions??><#list requiredActions><#items as reqActionItem>${msg("requiredAction.${reqActionItem}")}<#sep>, </#items></#list></#if></#assign>
<@layout.emailLayout eyebrow=msg("lhEmailActionsEyebrow") title=msg("lhEmailActionsTitle") preheader=msg("lhEmailActionsText", requiredActionsText)>
<@layout.text>${msg("lhEmailActionsText", requiredActionsText)}</@layout.text>
<@layout.action href=link label=msg("lhEmailActionsButton") />
<@layout.note>${msg("lhEmailExpires", linkExpirationFormatter(linkExpiration))}</@layout.note>
<@layout.note>${msg("lhEmailActionsIgnore")}</@layout.note>
</@layout.emailLayout>

<#ftl output_format="HTML">
<#import "template.ftl" as layout>
<@layout.emailLayout eyebrow=msg("lhEmailPasswordResetEyebrow") title=msg("lhEmailPasswordResetTitle") preheader=msg("lhEmailPasswordResetText")>
<@layout.text>${msg("lhEmailPasswordResetText")}</@layout.text>
<@layout.action href=link label=msg("lhEmailPasswordResetButton") />
<@layout.note>${msg("lhEmailExpires", linkExpirationFormatter(linkExpiration))}</@layout.note>
<@layout.note>${msg("lhEmailPasswordResetIgnore")}</@layout.note>
</@layout.emailLayout>

<#ftl output_format="HTML">
<#import "template.ftl" as layout>
<@layout.emailLayout eyebrow=msg("lhEmailUpdateEyebrow") title=msg("lhEmailUpdateTitle") preheader=msg("lhEmailUpdateText", newEmail)>
<@layout.text>${msg("lhEmailUpdateText", newEmail)}</@layout.text>
<@layout.action href=link label=msg("lhEmailUpdateButton") />
<@layout.note>${msg("lhEmailExpires", linkExpirationFormatter(linkExpiration))}</@layout.note>
<@layout.note>${msg("lhEmailUpdateIgnore")}</@layout.note>
</@layout.emailLayout>

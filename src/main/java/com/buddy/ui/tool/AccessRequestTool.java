package com.buddy.ui.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tool for creating access requests to portals
 * AI will use this tool only after collecting portal name and reason from user
 * and getting user confirmation
 */
@Component
@Slf4j
public class AccessRequestTool {
    
    /**
     * Creates an access request for a specific portal
     * 
     * @param portalName The name of the portal for which access is requested
     * @param reason The reason for requesting access
     * @return A confirmation message
     */
    @Tool("Portal erişim talebi oluşturur. Bu fonksiyonu çağırmadan önce kullanıcıdan portal adı ve neden bilgilerini toplamalısın. Bilgiler toplandıktan sonra direkt bu fonksiyonu çağır.")
    public String createAccessRequest(String portalName, String reason) {
        log.info("🎯 TOOL CALLED: createAccessRequest - Portal: {}, Reason: {}", portalName, reason);
        
        // TODO: Implement actual access request creation logic
        // This could call an external API, save to database, etc.
        
        String message = String.format(
            "✅ Erişim talebi başarıyla oluşturuldu!\n\n" +
            "📋 Portal: %s\n" +
            "📝 Neden: %s\n\n" +
            "Talebiniz ilgili ekibe iletildi. Onay süreci hakkında bilgilendirileceksiniz.",
            portalName, reason
        );
        
        log.info("✅ Access request created successfully");
        return message;
    }
}


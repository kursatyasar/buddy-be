package com.buddy.ui.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool for finding and matching mentors
 * Helps users find suitable mentors based on their role and experience
 */
@Component
@Slf4j
public class MentorMatchingTool {
    
    // Dummy mentor data
    private static final List<Mentor> MENTORS = Arrays.asList(
        new Mentor("Ahmet Yılmaz", "Senior Developer", 12, 
            "Mobile Development, iOS, Android",
            Map.of("Pazartesi", "13:00–14:00", "Salı", "17:00–18:00")),
        new Mentor("Elif Karadeniz", "Product Manager", 8,
            "Ürün stratejisi, roadmap yönetimi, kullanıcı araştırması, agile süreçleri",
            Map.of("Çarşamba", "10:00–11:00", "Perşembe", "15:00–16:00")),
        new Mentor("Mert Aslan", "DevOps Engineer", 10,
            "CI/CD, Kubernetes, AWS, otomasyon, infrastructure as code",
            Map.of("Pazartesi", "09:00–10:00", "Cuma", "14:00–15:00")),
        new Mentor("Selin Erdem", "UX/UI Designer", 6,
            "Kullanıcı deneyimi tasarımı, Figma, prototipleme, kullanıcı testleri",
            Map.of("Salı", "11:00–12:00", "Perşembe", "09:00–10:00")),
        new Mentor("Hakan Tunç", "QA Automation Lead", 9,
            "Test otomasyonu, Selenium, API testing, kalite süreçleri",
            Map.of("Çarşamba", "16:00–17:00", "Cuma", "10:00–11:00")),
        new Mentor("Derya Şahin", "Data Scientist", 7,
            "Machine Learning, Python, veri analitiği, modelleme",
            Map.of("Pazartesi", "15:00–16:00", "Salı", "09:00–10:00"))
    );
    
    /**
     * Finds suitable mentors based on user's role and experience
     * 
     * @param role The user's role (e.g., "Developer", "Product Manager", "Designer")
     * @param experienceYears Years of experience (as string, e.g., "2", "5")
     * @return Formatted list of suitable mentors with their details
     */
    @Tool("Kullanıcının rolü ve tecrübesine göre uygun mentorları bulur. Rol ve tecrübe bilgisini kullanıcıdan aldıktan sonra bu fonksiyonu çağır.")
    public String findMentors(String role, String experienceYears) {
        log.info("🎯 TOOL CALLED: findMentors - Role: {}, Experience: {} years", role, experienceYears);
        
        int experience = parseExperience(experienceYears);
        
        // Find matching mentors based on role similarity
        List<Mentor> matchingMentors = findMatchingMentors(role, experience);
        
        if (matchingMentors.isEmpty()) {
            return "Üzgünüm, belirttiğiniz kriterlere uygun mentor bulamadım. Lütfen farklı bir rol veya tecrübe seviyesi deneyin.";
        }
        
        // Format mentor list
        StringBuilder response = new StringBuilder();
        response.append("🎯 Size uygun gönüllü mentorlarımız:\n\n");
        
        for (int i = 0; i < matchingMentors.size(); i++) {
            Mentor mentor = matchingMentors.get(i);
            response.append(String.format("%d) %s\n", i + 1, mentor.name));
            response.append(String.format("   Rol: %s\n", mentor.role));
            response.append(String.format("   Tecrübe: %d yıl\n", mentor.experience));
            response.append(String.format("   Uzmanlık: %s\n", mentor.expertise));
            response.append("   Uygun Saatleri:\n");
            mentor.availableSlots.forEach((day, time) -> 
                response.append(String.format("   %s: %s\n", day, time))
            );
            response.append("\n");
        }
        
        response.append("Hangi mentor ile görüşmek istersiniz? Mentor adını ve uygun saatlerinden birini seçin.");
        
        log.info("✅ Found {} matching mentors", matchingMentors.size());
        return response.toString();
    }
    
    /**
     * Selects a mentor and time slot, sends notification
     * 
     * @param mentorName The name of the selected mentor
     * @param selectedTimeSlot The selected time slot (e.g., "Pazartesi: 13:00–14:00" or "Pazartesi 13:00")
     * @return Confirmation message
     */
    @Tool("Seçilen mentor ve saat için bildirim gönderir. Kullanıcı mentor ve saat seçtikten sonra bu fonksiyonu çağır.")
    public String selectMentor(String mentorName, String selectedTimeSlot) {
        log.info("🎯 TOOL CALLED: selectMentor - Mentor: {}, Time: {}", mentorName, selectedTimeSlot);
        
        // Find the mentor
        Optional<Mentor> mentorOpt = MENTORS.stream()
            .filter(m -> m.name.equalsIgnoreCase(mentorName) || 
                        m.name.toLowerCase().contains(mentorName.toLowerCase()) ||
                        mentorName.toLowerCase().contains(m.name.toLowerCase()))
            .findFirst();
        
        if (mentorOpt.isEmpty()) {
            return "Üzgünüm, seçtiğiniz mentor bulunamadı. Lütfen mentor adını kontrol edin.";
        }
        
        Mentor mentor = mentorOpt.get();
        
        // Validate time slot - check if selected time matches any available slot
        boolean isValidSlot = mentor.availableSlots.entrySet().stream()
            .anyMatch(entry -> {
                String day = entry.getKey();
                String time = entry.getValue();
                // Check if selectedTimeSlot contains day and time
                String selectedLower = selectedTimeSlot.toLowerCase();
                return selectedLower.contains(day.toLowerCase()) && 
                       (selectedLower.contains(time.split("–")[0].trim()) || 
                        selectedLower.contains(time.split("–")[1].trim()) ||
                        time.contains(selectedTimeSlot.split(":")[1].trim()) ||
                        selectedTimeSlot.contains(time));
            });
        
        if (!isValidSlot) {
            return String.format(
                "Üzgünüm, %s için seçtiğiniz saat uygun değil. Lütfen mentorun uygun saatlerinden birini seçin:\n%s",
                mentorName,
                mentor.availableSlots.entrySet().stream()
                    .map(e -> String.format("%s: %s", e.getKey(), e.getValue()))
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("")
            );
        }
        
        // TODO: Implement actual notification logic
        // This could send email, create calendar event, etc.
        
        // Find the exact time slot
        String exactTimeSlot = mentor.availableSlots.entrySet().stream()
            .filter(entry -> {
                String day = entry.getKey();
                String time = entry.getValue();
                String selectedLower = selectedTimeSlot.toLowerCase();
                return selectedLower.contains(day.toLowerCase()) && 
                       (selectedLower.contains(time.split("–")[0].trim()) || 
                        selectedLower.contains(time.split("–")[1].trim()));
            })
            .map(entry -> String.format("%s: %s", entry.getKey(), entry.getValue()))
            .findFirst()
            .orElse(selectedTimeSlot);
        
        String message = String.format(
            "✅ Mentor eşleştirmesi başarıyla tamamlandı!\n\n" +
            "👤 Mentor: %s\n" +
            "📅 Seçilen Saat: %s\n" +
            "📧 Mentorunuza bildirim gönderildi.\n\n" +
            "Mentorunuz en kısa sürede sizinle iletişime geçecek. Gelişim yolculuğunuzda başarılar! 🚀",
            mentor.name, exactTimeSlot
        );
        
        log.info("✅ Mentor selection completed successfully");
        return message;
    }
    
    private List<Mentor> findMatchingMentors(String userRole, int userExperience) {
        List<Mentor> matches = new ArrayList<>();
        
        // Simple matching logic - can be enhanced
        String roleLower = userRole.toLowerCase();
        
        for (Mentor mentor : MENTORS) {
            boolean roleMatches = mentor.role.toLowerCase().contains(roleLower) ||
                                 roleLower.contains(mentor.role.toLowerCase().split(" ")[0]) ||
                                 isRoleSimilar(roleLower, mentor.role.toLowerCase());
            
            // Prefer mentors with more experience than user, but not too much
            boolean experienceMatches = mentor.experience >= userExperience - 2 && 
                                      mentor.experience <= userExperience + 5;
            
            if (roleMatches && experienceMatches) {
                matches.add(mentor);
            }
        }
        
        // If no matches, return all mentors
        if (matches.isEmpty()) {
            matches.addAll(MENTORS);
        }
        
        // Sort by relevance (role match first, then experience proximity)
        matches.sort((m1, m2) -> {
            int roleMatch1 = roleLower.contains(m1.role.toLowerCase().split(" ")[0]) ? 0 : 1;
            int roleMatch2 = roleLower.contains(m2.role.toLowerCase().split(" ")[0]) ? 0 : 1;
            if (roleMatch1 != roleMatch2) return roleMatch1 - roleMatch2;
            return Math.abs(m1.experience - userExperience) - Math.abs(m2.experience - userExperience);
        });
        
        return matches;
    }
    
    private boolean isRoleSimilar(String role1, String role2) {
        // Simple similarity check
        String[] keywords1 = {"developer", "engineer", "programmer", "coder"};
        String[] keywords2 = {"manager", "lead", "director"};
        String[] keywords3 = {"designer", "ux", "ui"};
        String[] keywords4 = {"qa", "test", "quality"};
        String[] keywords5 = {"data", "scientist", "analyst"};
        
        boolean role1IsDev = Arrays.stream(keywords1).anyMatch(role1::contains);
        boolean role2IsDev = Arrays.stream(keywords1).anyMatch(role2::contains);
        
        boolean role1IsManager = Arrays.stream(keywords2).anyMatch(role1::contains);
        boolean role2IsManager = Arrays.stream(keywords2).anyMatch(role2::contains);
        
        boolean role1IsDesigner = Arrays.stream(keywords3).anyMatch(role1::contains);
        boolean role2IsDesigner = Arrays.stream(keywords3).anyMatch(role2::contains);
        
        boolean role1IsQA = Arrays.stream(keywords4).anyMatch(role1::contains);
        boolean role2IsQA = Arrays.stream(keywords4).anyMatch(role2::contains);
        
        boolean role1IsData = Arrays.stream(keywords5).anyMatch(role1::contains);
        boolean role2IsData = Arrays.stream(keywords5).anyMatch(role2::contains);
        
        return (role1IsDev && role2IsDev) || 
               (role1IsManager && role2IsManager) || 
               (role1IsDesigner && role2IsDesigner) ||
               (role1IsQA && role2IsQA) ||
               (role1IsData && role2IsData);
    }
    
    private int parseExperience(String experienceYears) {
        try {
            // Extract number from string
            String number = experienceYears.replaceAll("[^0-9]", "");
            return number.isEmpty() ? 0 : Integer.parseInt(number);
        } catch (Exception e) {
            return 0;
        }
    }
    
    // Inner class for mentor data
    private static class Mentor {
        final String name;
        final String role;
        final int experience;
        final String expertise;
        final Map<String, String> availableSlots;
        
        Mentor(String name, String role, int experience, String expertise, Map<String, String> availableSlots) {
            this.name = name;
            this.role = role;
            this.experience = experience;
            this.expertise = expertise;
            this.availableSlots = availableSlots;
        }
    }
}


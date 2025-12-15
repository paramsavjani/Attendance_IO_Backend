package com.attendanceio.api.service

import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
class CustomOAuth2UserService(
    private val studentRepositoryAppAction: StudentRepositoryAppAction
) : DefaultOAuth2UserService() {

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oauth2User = super.loadUser(userRequest)
        
        val email = oauth2User.getAttribute<String>("email") ?: ""
        val picture = oauth2User.getAttribute<String>("picture")
        val googleId = oauth2User.getAttribute<String>("sub") ?: ""

        // Validate email domain
        if (!email.endsWith("@dau.ac.in")) {
            throw OAuth2AuthenticationException("Only @dau.ac.in email addresses are allowed")
        }

        // Find or create student
        var student = studentRepositoryAppAction.findByEmail(email)
        
        if (student == null) {
            student = DMStudent().apply {
                this.email = email
                this.name = "Unknown"
                this.pictureUrl = picture
                this.googleId = googleId
                this.sid = email.split("@")[0]
            }
            studentRepositoryAppAction.create(student)
        } else {
                student.pictureUrl = picture
                if (student.googleId.isNullOrEmpty()) {
                    student.googleId = googleId
                }
                studentRepositoryAppAction.update(student)
        }

        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
        
        return DefaultOAuth2User(
            authorities,
            oauth2User.attributes,
            "sub"
        )
    }
}

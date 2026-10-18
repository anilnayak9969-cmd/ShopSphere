package com.example.ecommerce.services;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.common.ContactMessageResponse;
import com.example.ecommerce.dtos.common.ContactRequest;
import com.example.ecommerce.dtos.common.MessageResponse;
import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.entites.ContactMessage;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.ContactMessageRepository;

import lombok.RequiredArgsConstructor;

/** Contact Us form: stored in the database and shown to admins under Messages. */
@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactMessageRepository repository;
    private final NotificationService notificationService;

    @Transactional
    public MessageResponse submit(ContactRequest r) {
        ContactMessage m = new ContactMessage();
        m.setName(r.name().trim());
        m.setEmail(r.email().trim().toLowerCase());
        m.setSubject(r.subject().trim());
        m.setMessage(r.message().trim());
        repository.save(m);
        notificationService.notifyAdmins("New contact message", m.getName() + ": " + m.getSubject(), "/admin/messages");
        return new MessageResponse("Thanks, " + m.getName() + ". Your message has been received.");
    }

    @Transactional(readOnly = true)
    public PageResponse<ContactMessageResponse> list(Boolean resolved, int page, int size) {
        return PageResponse.of(repository.search(resolved, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))),
                ContactMessageResponse::from);
    }

    @Transactional
    public ContactMessageResponse setResolved(Long id, boolean resolved) {
        ContactMessage m = repository.findById(id).orElseThrow(() -> ApiException.notFound("Message not found."));
        m.setResolved(resolved);
        return ContactMessageResponse.from(repository.saveAndFlush(m));
    }
}

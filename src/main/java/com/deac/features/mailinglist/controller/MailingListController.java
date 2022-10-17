package com.deac.features.mailinglist.controller;

import com.deac.exception.MyException;
import com.deac.features.mailinglist.dto.UnsubscribeDto;
import com.deac.features.mailinglist.service.MailingListService;
import com.deac.response.ResponseMessage;
import com.deac.user.service.Language;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MailingListController {

    private final MailingListService mailingListService;

    @Autowired
    public MailingListController(MailingListService mailingListService) {
        this.mailingListService = mailingListService;
    }

    @PostMapping("/api/mailinglist/client/check_subscription")
    public boolean isClientSubscribed() {
        return mailingListService.isClientSubscribed();
    }

    @PostMapping("/api/mailinglist/client/subscribe")
    public ResponseMessage clientSubscribeToMailingList() {
        return new ResponseMessage(mailingListService.clientSubscribeToMailingList());
    }

    @PostMapping("/api/mailinglist/client/unsubscribe")
    public ResponseMessage clientUnsubscribeFromMailingList() {
        return new ResponseMessage(mailingListService.clientUnsubscribeFromMailingList());
    }

    @PostMapping("/api/mailinglist/subscribe")
    public ResponseMessage subscribeToMailingList(@RequestBody String email, @RequestParam("language") String language) {
        try {
            Language converted = Language.valueOf(language.toUpperCase());
            return new ResponseMessage(mailingListService.subscribeToMailingList(email, converted));
        } catch (IllegalArgumentException e) {
            throw new MyException("Unsupported language", HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/api/mailinglist/unsubscribe")
    public ResponseMessage unsubscribeFromMailingList(@RequestBody UnsubscribeDto unsubscribeDto) {
        return new ResponseMessage(mailingListService.unsubscribeFromMailingList(unsubscribeDto));
    }

}

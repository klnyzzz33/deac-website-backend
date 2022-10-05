package com.deac.features.mailinglist.controller;

import com.deac.features.mailinglist.dto.UnsubscribeDto;
import com.deac.features.mailinglist.service.MailingListService;
import com.deac.response.ResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public ResponseMessage subscribeToMailingList(@RequestBody String email) {
        return new ResponseMessage(mailingListService.subscribeToMailingList(email));
    }

    @PostMapping("/api/mailinglist/unsubscribe")
    public ResponseMessage unsubscribeFromMailingList(@RequestBody UnsubscribeDto unsubscribeDto) {
        return new ResponseMessage(mailingListService.unsubscribeFromMailingList(unsubscribeDto));
    }

}

package com.deac.features.mailinglist.service;

import com.deac.features.mailinglist.dto.UnsubscribeDto;

public interface MailingListService {
    boolean isClientSubscribed();

    String clientSubscribeToMailingList();

    String clientUnsubscribeFromMailingList();

    String subscribeToMailingList(String email);

    String unsubscribeFromMailingList(UnsubscribeDto unsubscribeDto);
}

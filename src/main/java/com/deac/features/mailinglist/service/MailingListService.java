package com.deac.features.mailinglist.service;

import com.deac.features.mailinglist.dto.UnsubscribeDto;
import com.deac.user.service.Language;

public interface MailingListService {
    boolean isClientSubscribed();

    String clientSubscribeToMailingList();

    String clientUnsubscribeFromMailingList();

    String subscribeToMailingList(String email, Language language);

    String unsubscribeFromMailingList(UnsubscribeDto unsubscribeDto);
}

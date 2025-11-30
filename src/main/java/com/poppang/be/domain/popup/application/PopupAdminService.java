package com.poppang.be.domain.popup.application;

import com.poppang.be.common.enums.Role;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PopupAdminService {

    private final UsersRepository usersRepository;
    private final PopupRepository popupRepository;

    @Transactional
    public void deactivatePopup(String userUuid, String popupUuid) {

        Users user = usersRepository.findByUuid(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        if (user.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("관리자만 사용할 수 있는 기능입니다. ");
        }

        Popup popup = popupRepository.findByUuid(popupUuid)
                .orElseThrow(() -> new IllegalArgumentException("팝업을 찾을 수 없습니다. "));

        popup.deactivate();
    }

}

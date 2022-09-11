package be.podor.musical.service;

import be.podor.musical.dto.MusicalListResponseDto;
import be.podor.musical.model.Musical;
import be.podor.musical.repository.MusicalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MusicalService {

    private final MusicalRepository musicalRepository;

    // 메인화면 상영중 뮤지컬 가져오기
    public List<MusicalListResponseDto> getOpenMusical() {
        List<Musical> musicals = musicalRepository.findTop10ByOrderByOpenDateDesc();

        return musicals.stream()
                .map(MusicalListResponseDto::of)
                .collect(Collectors.toList());
    }
}

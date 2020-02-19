package io.spring.cloud.samples.animalrescue.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnimalController {

	private static final Logger LOGGER = LoggerFactory.getLogger(AnimalController.class);

	private final AnimalRepository animalRepository;

	public AnimalController(AnimalRepository animalRepository) {
		this.animalRepository = animalRepository;
	}

	@GetMapping("/whoami")
	public Mono<String> whoami() {
		return ReactiveSecurityContextHolder
			.getContext()
			.map(SecurityContext::getAuthentication)
			.map(this::getUserName);
	}

	@GetMapping("/animals")
	public Flux<Animal> getAllAnimals() {
		LOGGER.info("Received get all animals request");
		return Flux.fromIterable(animalRepository.findAll());
	}

	@PostMapping("/animals/{id}/adoption-requests")
	@ResponseStatus(HttpStatus.CREATED)
	public Mono<Void> submitAdoptionRequest(
		@PathVariable("id") Long animalId,
		@RequestBody AdoptionRequest adoptionRequest
	) {
		return ReactiveSecurityContextHolder
			.getContext()
			.map(SecurityContext::getAuthentication)
			.flatMap(authentication -> {
				LOGGER.info("Received submit adoption request from {}",
					getUserName(authentication));
				Animal animal = animalRepository
					.findById(animalId)
					.orElseThrow(() ->
						new IllegalArgumentException(String.format("Animal with id %s doesn't exist!", animalId)));

				adoptionRequest.setAdopterName(getUserName(authentication));
				animal.getAdoptionRequests().add(adoptionRequest);
				animalRepository.save(animal);

				return Mono.empty();
			});
	}

	@PutMapping("/animals/{animalId}/adoption-requests/{adoptionRequestId}")
	public Mono<Void> editAdoptionRequest(
		@PathVariable("animalId") Long animalId,
		@PathVariable("adoptionRequestId") Long adoptionRequestId,
		@RequestBody AdoptionRequest adoptionRequest
	) {
		return ReactiveSecurityContextHolder
			.getContext()
			.map(SecurityContext::getAuthentication)
			.flatMap(authentication -> {
				LOGGER.info("Received edit adoption request");
				Animal animal = animalRepository
					.findById(animalId)
					.orElseThrow(() ->
						new IllegalArgumentException(
							String.format("Animal with id %s doesn't exist!", animalId)));

				AdoptionRequest existing = animal
					.getAdoptionRequests()
					.stream()
					.filter(ar -> ar.getId().equals(adoptionRequestId))
					.findAny()
					.orElseThrow(
						() -> new IllegalArgumentException(
							String.format("AdoptionRequest with id %s doesn't exist!",
								adoptionRequestId)));


				if (!existing.getAdopterName().equals(getUserName(authentication))) {
					throw new AccessDeniedException(
						String.format("User %s has cannot edit user %s's adoption request",
							getUserName(authentication), existing.getAdopterName()));
				}

				existing.setEmail(adoptionRequest.getEmail());
				existing.setNotes(adoptionRequest.getNotes());

				animalRepository.save(animal);
				return Mono.empty();
			});
	}

	@DeleteMapping("/animals/{animalId}/adoption-requests/{adoptionRequestId}")
	public Mono<Void> deleteAdoptionRequest(
		@PathVariable("animalId") Long animalId,
		@PathVariable("adoptionRequestId") Long adoptionRequestId
	) {
		return ReactiveSecurityContextHolder
			.getContext()
			.map(SecurityContext::getAuthentication)
			.flatMap(authentication -> {
				LOGGER.info("Received delete adoption request from {}", getUserName(authentication));
				Animal animal = animalRepository
					.findById(animalId)
					.orElseThrow(() ->
						new IllegalArgumentException(
							String.format("Animal with id %s doesn't exist!", animalId)));

				AdoptionRequest existing = animal
					.getAdoptionRequests()
					.stream()
					.filter(ar -> ar.getId().equals(adoptionRequestId))
					.findAny()
					.orElseThrow(
						() -> new IllegalArgumentException(
							String.format("AdoptionRequest with id %s doesn't exist!",
								adoptionRequestId)));

				if (!existing.getAdopterName().equals(getUserName(authentication))) {
					throw new AccessDeniedException(
						String.format("User %s has cannot delete user %s's adoption request",
							getUserName(authentication), existing.getAdopterName()));
				}

				animal.getAdoptionRequests().remove(existing);
				animalRepository.save(animal);
				return Mono.empty();
			});
	}

	@ExceptionHandler({AccessDeniedException.class})
	public ResponseEntity<String> handleAccessDeniedException(Exception e) {
		return new ResponseEntity<>(e.getMessage(), new HttpHeaders(), HttpStatus.FORBIDDEN);
	}

	private String getUserName(Authentication authentication) {
		LOGGER.info("getUserName from " + authentication);
		if (authentication instanceof JwtAuthenticationToken) {
			return ((JwtAuthenticationToken) authentication).getTokenAttributes().get("user_name")
				.toString();
		}
		else {
			return authentication.getName();
		}
	}
}

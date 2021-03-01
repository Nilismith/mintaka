package org.fiware.mintaka.rest;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fiware.mintaka.context.LdContextCache;
import org.fiware.mintaka.domain.ApiDomainMapper;
import org.fiware.mintaka.domain.query.GeoQuery;
import org.fiware.mintaka.domain.query.Geometry;
import org.fiware.mintaka.domain.TimeQuery;
import org.fiware.mintaka.domain.query.QueryParser;
import org.fiware.mintaka.domain.query.QueryTerm;
import org.fiware.mintaka.service.EntityTemporalService;
import org.fiware.ngsi.api.TemporalRetrievalApi;
import org.fiware.ngsi.model.EntityTemporalListVO;
import org.fiware.ngsi.model.EntityTemporalVO;
import org.fiware.ngsi.model.TimerelVO;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of the NGSI-LD temporal retrieval api
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class TemporalApiController implements TemporalRetrievalApi {

	public static final List<String> WELL_KNOWN_ATTRIBUTES = List.of("location", "observationSpace", "operationSpace", "unitCode");
	private static final String DEFAULT_TIME_PROPERTY = "observedAt";
	private static final String DEFAULT_GEO_PROPERTY = "location";
	private static final String SYS_ATTRS_OPTION = "sysAttrs";
	private static final String TEMPORAL_VALUES_OPTION = "temporalValues";
	public static final String COMMA_SEPERATOR = ",";

	private final EntityTemporalService entityTemporalService;
	private final LdContextCache contextCache;
	private final QueryParser queryParser;
	private final ApiDomainMapper apiDomainMapper;

	@Override
	public HttpResponse<EntityTemporalListVO> queryTemporalEntities(@Nullable String link, @Nullable URI id, @Nullable String idPattern, @Nullable @Size(min = 1) String type, @Nullable @Size(min = 1) String attrs, @Nullable @Size(min = 1) String q, @Nullable String georel, @Nullable String geometry, @Nullable String coordinates, @Nullable @Size(min = 1) String geoproperty, @Nullable TimerelVO timerel, @Nullable @Pattern(regexp = "^((\\d|[a-zA-Z]|_)+(:(\\d|[a-zA-Z]|_)+)?(#\\d+)?)$") @Size(min = 1) String timeproperty, @Nullable Instant time, @Nullable Instant endTime, @Nullable @Size(min = 1) String csf, @Nullable @Min(1) Integer limit, @Nullable String options, @Nullable @Min(1) Integer lastN) {

		List<URL> contextUrls = LdContextCache.getContextURLsFromLinkHeader(link);
		String expandedGeoProperty = Optional.ofNullable(geoproperty)
				.filter(property -> !WELL_KNOWN_ATTRIBUTES.contains(property))
				.map(property -> contextCache.expandString(property, contextUrls))
				.orElse(DEFAULT_GEO_PROPERTY);
		TimeQuery timeQuery = new TimeQuery(apiDomainMapper.timeRelVoToTimeRelation(timerel), time, endTime, getTimeRelevantProperty(timeproperty));

		List<EntityTemporalVO> entityTemporalVOS = entityTemporalService.getEntitiesWithQuery(
				Optional.ofNullable(idPattern),
				getExpandedTypes(contextUrls, type),
				getExpandedAttributes(contextUrls, attrs),
				Optional.ofNullable(q).map(queryString -> queryParser.toTerm(queryString, contextUrls)),
				getGeometryQuery(georel, geometry, coordinates, expandedGeoProperty),
				timeQuery,
				lastN,
				isSysAttrs(options),
				isTemporalValuesOptionSet(options));
		entityTemporalVOS.forEach(entityTemporalVO -> addContextToEntityTemporalVO(entityTemporalVO, contextUrls));

		EntityTemporalListVO entityTemporalListVO = new EntityTemporalListVO();
		entityTemporalListVO.addAll(entityTemporalVOS);
		return HttpResponse.ok(entityTemporalListVO);
	}

	@Override
	public HttpResponse<EntityTemporalVO> retrieveEntityTemporalById(URI entityId, @Nullable String link, @Nullable @Size(min = 1) String attrs, @Nullable String options, @Nullable TimerelVO timerel, @Nullable @Pattern(regexp = "^((\\d|[a-zA-Z]|_)+(:(\\d|[a-zA-Z]|_)+)?(#\\d+)?)$") @Size(min = 1) String timeproperty, @Nullable Instant time, @Nullable Instant endTime, @Nullable @Min(1) Integer lastN) {

		List<URL> contextUrls = LdContextCache.getContextURLsFromLinkHeader(link);
		TimeQuery timeQuery = new TimeQuery(apiDomainMapper.timeRelVoToTimeRelation(timerel), time, endTime, getTimeRelevantProperty(timeproperty));

		Optional<EntityTemporalVO> entityTemporalVOOptional = entityTemporalService
				.getNgsiEntitiesWithTimerel(entityId.toString(),
						timeQuery,
						getExpandedAttributes(contextUrls, attrs),
						lastN,
						isSysAttrs(options),
						isTemporalValuesOptionSet(options));
		if (entityTemporalVOOptional.isEmpty()) {
			return HttpResponse.notFound();
		} else {
			return HttpResponse.ok(addContextToEntityTemporalVO(entityTemporalVOOptional.get(), contextUrls));
		}
	}

	private EntityTemporalVO addContextToEntityTemporalVO(EntityTemporalVO entityTemporalVO, List<URL> contextUrls) {
		if (contextUrls.size() > 1) {
			entityTemporalVO.atContext(contextUrls);
		} else {
			entityTemporalVO.atContext(contextUrls.get(0));
		}
		return entityTemporalVO;
	}

	private List<String> getExpandedAttributes(List<URL> contextUrls, String attrs) {
		return Optional.ofNullable(attrs)
				.map(al -> contextCache.expandStrings(Arrays.asList(attrs.split(COMMA_SEPERATOR)), contextUrls))
				.orElse(List.of());
	}

	private List<String> getExpandedTypes(List<URL> contextUrls, String types) {
		return Optional.ofNullable(types)
				.map(al -> contextCache.expandStrings(Arrays.asList(types.split(COMMA_SEPERATOR)), contextUrls))
				.orElse(List.of());
	}

	/**
	 * Get the timeProperty string or the default property if null
	 *
	 * @param timeProperty timeProperty retrieved through the api
	 * @return timeProperty to be used
	 */
	private String getTimeRelevantProperty(String timeProperty) {
		return Optional.ofNullable(timeProperty).orElse(DEFAULT_TIME_PROPERTY);
	}

	private boolean isSysAttrs(String options) {
		if (options == null) {
			return false;
		}
		return Arrays.asList(options.split(COMMA_SEPERATOR)).contains(SYS_ATTRS_OPTION);
	}

	private boolean isTemporalValuesOptionSet(String options) {
		Optional<String> optionalOptions = Optional.ofNullable(options);
		if (optionalOptions.isEmpty()) {
			return false;
		}
		return Arrays.asList(options.split(COMMA_SEPERATOR)).contains(TEMPORAL_VALUES_OPTION);
	}

	private Optional<GeoQuery> getGeometryQuery(String georel, String geometry, String coordinates, String geoproperty) {
		if (georel == null && coordinates == null && geometry == null) {
			return Optional.empty();
		}

		if (georel == null || coordinates == null || geometry == null) {
			throw new IllegalArgumentException(
					String.format("When querying for geoRelations, all 3 parameters(georel: %s, coordinates: %s, geometry: %s) need to be present.", georel, coordinates, geometry));
		}

		return Optional.of(new GeoQuery(georel, Geometry.byName(geometry), coordinates, geoproperty));
	}
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package br.com.cpqd.processors.sample;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.state.Scope;
import org.apache.nifi.components.state.StateManager;
import org.apache.nifi.components.state.StateMap;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;

import com.jayway.jsonpath.JsonPath;

@Tags({ "example" })
@CapabilityDescription("Provide a description")
@SeeAlso({})
@ReadsAttributes({ @ReadsAttribute(attribute = "", description = "") })
@WritesAttributes({ @WritesAttribute(attribute = "", description = "") })
public class MyProcessor extends AbstractProcessor {

	public static final PropertyDescriptor MY_PROPERTY = new PropertyDescriptor.Builder().name("MY_PROPERTY")
			.displayName("My property").description("Example Property").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final Relationship MY_RELATIONSHIP = new Relationship.Builder().name("MY_RELATIONSHIP")
			.description("Example relationship").build();

	private List<PropertyDescriptor> descriptors;

	private Set<Relationship> relationships;

	@Override
	protected void init(final ProcessorInitializationContext context) {
		final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
		descriptors.add(MY_PROPERTY);
		this.descriptors = Collections.unmodifiableList(descriptors);

		final Set<Relationship> relationships = new HashSet<Relationship>();
		relationships.add(MY_RELATIONSHIP);
		this.relationships = Collections.unmodifiableSet(relationships);
	}

	@Override
	public Set<Relationship> getRelationships() {
		return this.relationships;
	}

	@Override
	public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
		return descriptors;
	}

	@OnScheduled
	public void onScheduled(final ProcessContext context) {

	}

	private void initVariables(HashMap<String, String> statePropertyMap) {

		for (Map.Entry<String, String> entry : statePropertyMap.entrySet()) {
			String key = entry.getKey();
//			String val = entry.getValue();
			statePropertyMap.put(key, "0");
		}
	}

	@Override
	public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {

		final ComponentLog logger = getLogger();

		final AtomicReference<Integer> contadorRegressivoAmostra = new AtomicReference<>();
		final AtomicReference<Integer> contadorPulso = new AtomicReference<>();
		final AtomicReference<Integer> correnteSurto = new AtomicReference<>();
		final AtomicReference<Integer> correnteFuga = new AtomicReference<>();
		final AtomicReference<Double> temperaturaAmb = new AtomicReference<>();
		final AtomicReference<Double> temperaturaVaristor = new AtomicReference<>();
		final AtomicReference<Integer> tensao = new AtomicReference<>();
		final AtomicReference<String> originatorDeviceId = new AtomicReference<>();

		final double capacidadeNominal = 10;

		FlowFile flowFile = session.get();
		if (flowFile == null) {
			return;
		}
		final StateManager stateManager = context.getStateManager();
		final StateMap stateMap;

		try {
			stateMap = stateManager.getState(Scope.LOCAL);
		} catch (final IOException ioe) {
			logger.error("Failed to retrieve observed maximum values from the State Manager. Will not perform "
					+ "query until this is accomplished.", ioe);
			context.yield();
			return;
		}

		final HashMap<String, String> statePropertyMap = new HashMap<>(stateMap.toMap());

		int leiturasAposSurto = -60;
		session.read(flowFile, new InputStreamCallback() {
			@Override
			public void process(InputStream in) throws IOException {
				try {
					String json = IOUtils.toString(in);

					originatorDeviceId.set(JsonPath.read(json, "$.metadata.deviceid"));
					contadorRegressivoAmostra.set(JsonPath.read(json, "$.attrs.contadorRegressivoAmostra"));
					contadorPulso.set(JsonPath.read(json, "$.attrs.contadorPulso"));
					correnteSurto.set(JsonPath.read(json, "$.attrs.correnteSurto"));
					correnteFuga.set(JsonPath.read(json, "$.attrs.correnteFuga"));
					if (JsonPath.read(json, "$.attrs.temperaturaAmb") instanceof Double) {
						temperaturaAmb.set(JsonPath.read(json, "$.attrs.temperaturaAmb"));
					} else {
						int temp = JsonPath.read(json, "$.attrs.temperaturaAmb");
						temperaturaAmb.set(Double.valueOf(temp));
					}
					if (JsonPath.read(json, "$.attrs.temperaturaVaristor") instanceof Double) {
						temperaturaVaristor.set(JsonPath.read(json, "$.attrs.temperaturaVaristor"));
					} else {
						int temp = JsonPath.read(json, "$.attrs.temperaturaVaristor");
						temperaturaVaristor.set(Double.valueOf(temp));
					}
					tensao.set(JsonPath.read(json, "$.attrs.tensao"));
					statePropertyMap.put(originatorDeviceId.get().toString() + "-desvioPadraoTemperaturaVaristor", "0");

					// se deu reset no contador de pulso
					if (contadorPulso.get().intValue() == 0) {
						initVariables(statePropertyMap);

					}

					// Se iniciou outro surto, ...
					if (statePropertyMap.get(originatorDeviceId.get().toString() + "-ultimoContadorPulso") != null) {
						if (!contadorPulso.get().toString().equals(
								statePropertyMap.get(originatorDeviceId.get().toString() + "-ultimoContadorPulso"))) {

							List<String> histCorrenteUltimoSurtoList = Arrays.asList(statePropertyMap
									.get(originatorDeviceId.get().toString() + "-histCorrenteUltimoSurto")
									.split("\\s"));
							List<Double> histCorrenteUltimoSurtoIntegerList = new ArrayList<>();
							if (histCorrenteUltimoSurtoList != null && histCorrenteUltimoSurtoList.size() > 0) {
								histCorrenteUltimoSurtoIntegerList = histCorrenteUltimoSurtoList.stream()
										.map(Double::parseDouble).collect(Collectors.toList());
							}

							List<String> histTensaoUltimoSurtoList = Arrays.asList(statePropertyMap
									.get(originatorDeviceId.get().toString() + "-histCorrenteUltimoSurto")
									.split("\\s"));
							List<Double> histTensaoUltimoSurtoIntegerList = histTensaoUltimoSurtoList.stream()
									.map(Double::parseDouble).collect(Collectors.toList());

							// Salva Valores acumulados no ulltimo surto
							statePropertyMap.put(originatorDeviceId.get().toString() + "-maxISurtoAcumulada",
									String.valueOf(Double
											.valueOf(statePropertyMap
													.get(originatorDeviceId.get().toString() + "-maxISurtoAcumulada"))
											+ Collections.max(histCorrenteUltimoSurtoIntegerList)));
							statePropertyMap.put(originatorDeviceId.get().toString() + "-maxTensaoSurtoAcumulada",
									String.valueOf(Double
											.valueOf(statePropertyMap.get(
													originatorDeviceId.get().toString() + "-maxTensaoSurtoAcumulada"))
											+ Collections.max(histTensaoUltimoSurtoIntegerList)));

							// zera o n..mero de observa....es desde o ..ltimo surto
							statePropertyMap.put(originatorDeviceId.get().toString() + "-histCorrenteUltimoSurto", "");
							statePropertyMap.put(originatorDeviceId.get().toString() + "-histTensaoUltimoSurto", "");
							statePropertyMap
									.put(originatorDeviceId.get().toString() + "-histEnergiaAcumuladaUltimoSurto", "");
							statePropertyMap.put(
									originatorDeviceId.get().toString() + "-histTemperaturaVaristorUltimoSurto", "");
							statePropertyMap.put(
									originatorDeviceId.get().toString() + "-histTemperaturaAmbienteUltimoSurto", "");

						}
					}

					// .. surto se ContadorRegressivoAmostra .. maior ou igual a zero
					boolean ehSurto = contadorRegressivoAmostra.get().intValue() >= 0;

					// Revisar se a conta abaixo est.. correta
					double tempo = 2.25000000; // 2.25 microsegundos
					double energiaLeitura = correnteSurto.get().doubleValue() * tensao.get().doubleValue() * tempo;

					// EnergiaAcumulada (desde o in..cio da opera....o)
					List<Double> histEnergiaAcumuladaDoubleList = new ArrayList<>();
					histEnergiaAcumuladaDoubleList.add(energiaLeitura);

					Optional<Double> energiaAcumulada = histEnergiaAcumuladaDoubleList.stream()
							.reduce((subtotal, element) -> subtotal + Math.abs(element));

					// PercJoulesCapacidadeNom
					double percJoulesCapacidadeNom = energiaAcumulada.get().doubleValue() / capacidadeNominal;

					List<String> histEnergiaAcumuladaStringList = histEnergiaAcumuladaDoubleList.stream()
							.map(String::valueOf).collect(Collectors.toList());

					String.join("\\s", histEnergiaAcumuladaStringList);

					addHistory(originatorDeviceId.get().toString() + "histTemperaturaVaristor",
							temperaturaVaristor.get().toString(), statePropertyMap);
					addHistory(originatorDeviceId.get().toString() + "histTemperaturaVaristor",
							temperaturaAmb.get().toString(), statePropertyMap);

					if (ehSurto) {
						addHistory(originatorDeviceId.get().toString() + "histCorrenteUltimoSurto",
								String.valueOf(Math.abs(correnteSurto.get().doubleValue())), statePropertyMap);
						addHistory(originatorDeviceId.get().toString() + "histTensaoUltimoSurto",
								String.valueOf(Math.abs(tensao.get().doubleValue())), statePropertyMap);
						addHistory(originatorDeviceId.get().toString() + "histEnergiaAcumuladaUltimoSurto",
								String.valueOf(Math.abs(energiaLeitura)), statePropertyMap);
						addHistory(originatorDeviceId.get().toString() + "histTemperaturaVaristorUltimoSurto",
								String.valueOf(Math.abs(temperaturaVaristor.get().intValue())), statePropertyMap);
						addHistory(originatorDeviceId.get().toString() + "histTemperaturaAmbienteUltimoSurto",
								String.valueOf(Math.abs(temperaturaAmb.get().doubleValue())), statePropertyMap);
					}

					List<Double> histCorrenteUltimoSurtoListDouble = getHistory("histCorrenteUltimoSurto",
							statePropertyMap);
					if (histCorrenteUltimoSurtoListDouble != null) {
						statePropertyMap.put(originatorDeviceId.get().toString() + "-maxIsurto",
								String.valueOf(Collections.max(histCorrenteUltimoSurtoListDouble)));
					}

					List<Double> histTensaoUltimoSurtoListDouble = getHistory("histTensaoUltimoSurto",
							statePropertyMap);

					if (histTensaoUltimoSurtoListDouble != null) {
						statePropertyMap.put(originatorDeviceId.get().toString() + "-maxTensaoSurto",
								String.valueOf(Collections.max(histTensaoUltimoSurtoListDouble)));
					}

					List<Double> histEnergiaAcumuladaUltimoSurtoListDouble = getHistory(
							"histEnergiaAcumuladaUltimoSurto", statePropertyMap);
					String energiaSurto = "";
					if (histEnergiaAcumuladaUltimoSurtoListDouble != null) {
						energiaSurto = histEnergiaAcumuladaUltimoSurtoListDouble.stream()
								.reduce((subtotal, element) -> subtotal + Math.abs(element)).get().toString();

					}

					List<Double> histTemperaturaAmbienteUltimoSurtoListDouble = getHistory(
							"histTemperaturaAmbienteUltimoSurto", statePropertyMap);

					double maxTemperaturaAmbienteSurto = 0.0d;
					if (histTemperaturaAmbienteUltimoSurtoListDouble != null) {
						maxTemperaturaAmbienteSurto = Collections.max(histTemperaturaAmbienteUltimoSurtoListDouble);
					}

					List<Double> histTemperaturaVaristorUltimoSurtoListDouble = getHistory(
							"histTemperaturaVaristorUltimoSurto", statePropertyMap);
					double maxTemperaturaVaristorSurto = 0.0d;
					if (histTemperaturaVaristorUltimoSurtoListDouble != null) {
						maxTemperaturaVaristorSurto = Collections.max(histTemperaturaVaristorUltimoSurtoListDouble);

					}
					double maxISurtoAcumulada = 0;
					if (statePropertyMap.get(originatorDeviceId.get().toString() + "-maxISurtoAcumulada") != null) {
						maxISurtoAcumulada = Double
								.valueOf(statePropertyMap
										.get(originatorDeviceId.get().toString() + "-maxISurtoAcumulada"))
								.doubleValue()
								+ Double.valueOf(
										statePropertyMap.get(originatorDeviceId.get().toString() + "-maxIsurto"))
										.doubleValue();
					} else {
						maxISurtoAcumulada = Double
								.valueOf(statePropertyMap.get(originatorDeviceId.get().toString() + "-maxIsurto"))
								.doubleValue();
					}

					double maxTensaoSurtoAcumulada = 0;
					if (statePropertyMap
							.get(originatorDeviceId.get().toString() + "-maxTensaoSurtoAcumulada") != null) {
						maxTensaoSurtoAcumulada = Double
								.valueOf(statePropertyMap
										.get(originatorDeviceId.get().toString() + "-maxTensaoSurtoAcumulada"))
								.doubleValue()
								+ Double.valueOf(
										statePropertyMap.get(originatorDeviceId.get().toString() + "-maxTensaoSurto"))
										.doubleValue();
					} else {
						maxTensaoSurtoAcumulada = Double
								.valueOf(statePropertyMap.get(originatorDeviceId.get().toString() + "-maxTensaoSurto"))
								.doubleValue();

					}

					// DifTempTerminalAmb
					double difTempTerminalAmb = 0.0;

					if (getHistory("histTemperaturaVaristorUltimoSurto", statePropertyMap) != null
							&& getHistory("histTemperaturaAmbienteUltimoSurto", statePropertyMap) != null) {
						if (getHistory("histTemperaturaVaristorUltimoSurto", statePropertyMap).size() > 0
								&& getHistory("histTemperaturaAmbienteUltimoSurto", statePropertyMap).size() > 0) {
							difTempTerminalAmb = getHistory("histTemperaturaVaristorUltimoSurto", statePropertyMap)
									.get(0) - getHistory("histTemperaturaAmbienteUltimoSurto", statePropertyMap).get(0);
						}
					}

					// Desvio Padrãoo
					double desvioPadraoCorrenteFuga = 0.0;

					List<Double> histCorrenteFugaListDouble = getHistory("histCorrenteFuga", statePropertyMap);
					if (histCorrenteFugaListDouble != null && histCorrenteFugaListDouble.size() > 0) {
						StandardDeviation sd = new StandardDeviation(false);
						double histCorrenteFugaArray[] = new double[histCorrenteFugaListDouble.size()];
						for (int i = 0; i < histCorrenteFugaListDouble.size(); i++) {
							histCorrenteFugaArray[i] = histCorrenteFugaListDouble.get(i).doubleValue();
						}
						desvioPadraoCorrenteFuga = sd.evaluate(histCorrenteFugaArray);
					}

					double txCorrenteFugaAtualSobAnterior = 0.0d;
					if (histCorrenteFugaListDouble != null && histCorrenteFugaListDouble.size() > 1) {
						int length = histCorrenteFugaListDouble.size();
						txCorrenteFugaAtualSobAnterior = histCorrenteFugaListDouble.get(length - 1)
								/ histCorrenteFugaListDouble.get(length - 2);
					}

					// TxCorrenteFugaAtualSob3Anterior
					double txCorrenteFugaAtualSob3Anterior = 0.0;
					if (histCorrenteFugaListDouble != null && histCorrenteFugaListDouble.size() > 3) {
						int length = histCorrenteFugaListDouble.size();
						txCorrenteFugaAtualSob3Anterior = histCorrenteFugaListDouble.get(length - 1)
								/ histCorrenteFugaListDouble.get(length - 4);
					}

					double desvioPadraoTemperaturaVaristor = 0.0;
					List<Double> histMaxTemperaturaVaristorListDouble = getHistory("histMaxTemperaturaVaristor",
							statePropertyMap);
					if (histMaxTemperaturaVaristorListDouble != null
							&& histMaxTemperaturaVaristorListDouble.size() > 0) {

						StandardDeviation sd = new StandardDeviation(false);
						double histMaxTemperaturaVaristorArray[] = new double[histMaxTemperaturaVaristorListDouble
								.size()];
						for (int i = 0; i < histMaxTemperaturaVaristorListDouble.size(); i++) {
							histMaxTemperaturaVaristorArray[i] = histMaxTemperaturaVaristorListDouble.get(i)
									.doubleValue();
						}
						desvioPadraoTemperaturaVaristor = sd.evaluate(histMaxTemperaturaVaristorArray);
					}

//					// DuracaoSurto (tempo em segundos que levou para gerar da energia do surto)
//					double duracaoSurto = Double.valueOf(statePropertyMap.get("energiaSurto"))
//							/ (Double.valueOf(statePropertyMap.get("maxIsurto"))
//									* Double.valueOf(statePropertyMap.get("maxTensaoSurto")));
					// Verificar se é o momento de enviar para a PlatIA
					boolean enviarPlatIA = (contadorRegressivoAmostra.get().intValue() == leiturasAposSurto)
							&& (contadorPulso.get().intValue() > 0);

					if (enviarPlatIA) {

						// Corrente de Fuga é medida após o término do surto.
						addHistory(originatorDeviceId.get().toString() + "histCorrenteFuga",
								correnteFuga.get().toString(), statePropertyMap);
						addHistory(originatorDeviceId.get().toString() + "histMaxTemperaturaVaristor",
								statePropertyMap
										.get(originatorDeviceId.get().toString() + "-maxTemperaturaVaristorSurto"),
								statePropertyMap);

					}

					statePropertyMap.put(originatorDeviceId.get().toString() + "-maxTemperaturaVaristorSurto",
							String.valueOf(maxTemperaturaVaristorSurto));
					statePropertyMap.put(originatorDeviceId.get().toString() + "-maxTemperaturaAmbienteSurto",
							String.valueOf(maxTemperaturaAmbienteSurto));
					statePropertyMap.put(originatorDeviceId.get().toString() + "-energiaSurto", energiaSurto);
					statePropertyMap.put(originatorDeviceId.get().toString() + "-ultimoContadorPulso",
							contadorPulso.get().toString());
					statePropertyMap.put(originatorDeviceId.get().toString() + "-ultimoContadorRegressivoAmostra",
							contadorRegressivoAmostra.get().toString());
					statePropertyMap.put(originatorDeviceId.get().toString() + "-enviarPlatIA",
							String.valueOf(enviarPlatIA));
					statePropertyMap.put(originatorDeviceId.get().toString() + "-desvioPadraoCorrenteFuga",
							String.valueOf(desvioPadraoCorrenteFuga));
					statePropertyMap.put(originatorDeviceId.get().toString() + "-energiaAcumulada",
							energiaAcumulada.get().toString());
					statePropertyMap.put(originatorDeviceId.get().toString() + "-maxISurtoAcumulada",
							String.valueOf(maxISurtoAcumulada));
					statePropertyMap.put(originatorDeviceId.get().toString() + "-maxTensaoSurtoAcumulada",
							String.valueOf(maxTensaoSurtoAcumulada));
					statePropertyMap.put(originatorDeviceId.get().toString() + "-percJoulesCapacidadeNom",
							String.valueOf(percJoulesCapacidadeNom));
					statePropertyMap.put(originatorDeviceId.get().toString() + "-difTempTerminalAmb",
							String.valueOf(difTempTerminalAmb));
					statePropertyMap.put(originatorDeviceId.get().toString() + "-desvioPadraoTemperaturaVaristor",
							String.valueOf(desvioPadraoTemperaturaVaristor));
					statePropertyMap.put(originatorDeviceId.get().toString() + "-txCorrenteFugaAtualSobAnterior",
							String.valueOf(txCorrenteFugaAtualSobAnterior));
					statePropertyMap.put(originatorDeviceId.get().toString() + "-txCorrenteFugaAtualSob3Anterior",
							String.valueOf(txCorrenteFugaAtualSob3Anterior));

					try {
						// Update the state
						stateManager.setState(statePropertyMap, Scope.LOCAL);
					} catch (IOException ioe) {
						logger.error("{} failed to update State Manager.", new Object[] { this, ioe });
					}

				} catch (Exception ex) {
					ex.printStackTrace();
					logger.error("{} failed to process.", new Object[] { this, ex });
				}
			}
		});

		session.putAttribute(flowFile, "desvioPadraoCorrenteFuga",
				statePropertyMap.get(originatorDeviceId.get().toString() + "-desvioPadraoCorrenteFuga"));
		session.putAttribute(flowFile, "capacidadeNominal", String.valueOf(capacidadeNominal));
		session.putAttribute(flowFile, "contadorPulso", contadorPulso.get().toString());
		session.putAttribute(flowFile, "correnteSurto", correnteSurto.get().toString());
		session.putAttribute(flowFile, "correnteFuga", correnteFuga.get().toString());
		session.putAttribute(flowFile, "temperaturaAmb", temperaturaAmb.get().toString());
		session.putAttribute(flowFile, "temperaturaVaristor", temperaturaVaristor.get().toString());
		session.putAttribute(flowFile, "tensao", tensao.get().toString());

		session.putAttribute(flowFile, "maxISurtoAcumulada",
				statePropertyMap.get(originatorDeviceId.get().toString() + "-maxISurtoAcumulada"));
		session.putAttribute(flowFile, "maxTensaoSurtoAcumulada",
				statePropertyMap.get(originatorDeviceId.get().toString() + "-maxTensaoSurtoAcumulada"));
		session.putAttribute(flowFile, "energiaAcumulada",
				statePropertyMap.get(originatorDeviceId.get().toString() + "-energiaAcumulada"));
		session.putAttribute(flowFile, "percJoulesCapacidadeNom",
				statePropertyMap.get(originatorDeviceId.get().toString() + "-percJoulesCapacidadeNom"));

		session.putAttribute(flowFile, "difTempTerminalAmb",
				statePropertyMap.get(originatorDeviceId.get().toString() + "-desvioPadraoTemperaturaVaristor"));
		session.putAttribute(flowFile, "desvioPadraoTemperaturaVaristor",
				statePropertyMap.get(originatorDeviceId.get().toString() + "-desvioPadraoTemperaturaVaristor"));
		session.putAttribute(flowFile, "txCorrenteFugaAtualSobAnterior",
				statePropertyMap.get(originatorDeviceId.get().toString() + "-txCorrenteFugaAtualSobAnterior"));
		session.putAttribute(flowFile, "txCorrenteFugaAtualSob3Anterior",
				statePropertyMap.get(originatorDeviceId.get().toString() + "-txCorrenteFugaAtualSob3Anterior"));
		session.putAttribute(flowFile, "energiaSurto",
				statePropertyMap.get(originatorDeviceId.get().toString() + "-energiaSurto"));
		session.putAttribute(flowFile, "maxIsurto",
				statePropertyMap.get(originatorDeviceId.get().toString() + "-maxIsurto"));
		session.putAttribute(flowFile, "maxTensaoSurto",
				statePropertyMap.get(originatorDeviceId.get().toString() + "-maxTensaoSurto"));
		session.putAttribute(flowFile, "maxTemperaturaAmbienteSurto",
				statePropertyMap.get(originatorDeviceId.get().toString() + "-maxTemperaturaAmbienteSurto"));
		session.putAttribute(flowFile, "maxTemperaturaVaristorSurto",
				statePropertyMap.get(originatorDeviceId.get().toString() + "-maxTemperaturaVaristorSurto"));
		session.putAttribute(flowFile, "ultimoContadorPulso",
				statePropertyMap.get(originatorDeviceId.get().toString() + "-ultimoContadorPulso"));
		session.putAttribute(flowFile, "ultimoContadorRegressivoAmostra",
				statePropertyMap.get(originatorDeviceId.get().toString() + "-ultimoContadorRegressivoAmostra"));
		session.putAttribute(flowFile, "originatorDeviceId", originatorDeviceId.get());

//		session.getProvenanceReporter().modifyContent(emptyFlowFile);
//		session.getProvenanceReporter().route(flowFile, MY_RELATIONSHIP);
		session.transfer(flowFile, MY_RELATIONSHIP);
		try {
			session.commit();
		} catch (Exception e) {
			logger.error("{} failed to update State Manager.", new Object[] { this, e });
		}

	}

	void addHistory(String name, String value, HashMap<String, String> statePropertyMap) {
		String history = statePropertyMap.get(name);
		if (history != null && !history.isEmpty()) {
			history = history + " " + value;
		} else {
			history = value;
		}
		statePropertyMap.put(name, history);
	}

	List<Double> getHistory(String name, HashMap<String, String> statePropertyMap) {

		String val = null;
		List<Double> listReturn = null;

		val = statePropertyMap.get(name);
		if (val != null) {
			if (!val.isEmpty()) {
				listReturn = Arrays.asList(val.split(" ")).stream().map(Double::parseDouble)
						.collect(Collectors.toList());
			}
		}
		return listReturn;

	}
}
